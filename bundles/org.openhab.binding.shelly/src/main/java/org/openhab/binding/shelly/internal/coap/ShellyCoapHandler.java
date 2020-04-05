/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.shelly.internal.coap;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.*;
import static org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.*;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsRelay;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotDescrBlk;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotDescrSen;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotDevDescription;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotGenericSensorList;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotSensor;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotSensorTypeAdapter;
import org.openhab.binding.shelly.internal.config.ShellyThingConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyBaseHandler;
import org.openhab.binding.shelly.internal.handler.ShellyColorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link ShellyCoapHandler} handles the CoIoT/Coap registration and events.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyCoapHandler implements ShellyCoapListener {
    private final Logger logger = LoggerFactory.getLogger(ShellyCoapHandler.class);

    private final ShellyBaseHandler th;
    private ShellyThingConfiguration config = new ShellyThingConfiguration();
    private final GsonBuilder gsonBuilder = new GsonBuilder();
    private final Gson gson;
    private String thingName;
    private boolean started = false;
    private boolean discovering = false;

    private boolean coapTriggersRefresh = false;
    private final ShellyCoapServer coapServer;
    private @Nullable CoapClient statusClient;
    private Request reqDescription = new Request(Code.GET, Type.CON);;
    private Request reqStatus = new Request(Code.GET, Type.CON);

    private int lastSerial = -1;
    private Double lastBrightness = -1.0;
    private String lastPayload = "";
    private Map<String, CoIotDescrBlk> blockMap = new HashMap<>();
    private SortedMap<String, CoIotDescrSen> sensorMap = new TreeMap<>();

    private static final byte[] EMPTY_BYTE = new byte[0];

    public ShellyCoapHandler(ShellyBaseHandler th, ShellyCoapServer coapServer) {
        this.th = th;
        this.coapServer = coapServer;
        this.thingName = th.thingName;

        gsonBuilder.registerTypeAdapter(CoIotGenericSensorList.class, new CoIotSensorTypeAdapter());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    /*
     * Initialize Coap access, send discovery packet and start Status server
     */
    public void start(String thingName, ShellyThingConfiguration config, boolean coapTriggersRefresh)
            throws ShellyApiException {
        if (isStarted()) {
            logger.trace("{}: Coap handler is already started", thingName);
            return;
        }
        try {
            this.thingName = thingName;
            this.config = config;
            this.coapTriggersRefresh = coapTriggersRefresh;
            reqDescription = sendRequest(reqDescription, config.deviceIp, COLOIT_URI_DEVDESC, Type.CON);

            if (statusClient == null) {
                coapServer.init(config.localIp);
                coapServer.addListener(this);

                statusClient = new CoapClient(completeUrl(config.deviceIp, COLOIT_URI_DEVSTATUS))
                        .setTimeout((long) SHELLY_API_TIMEOUT_MS).useNONs().setEndpoint(coapServer.getEndpoint());
            }
        } catch (UnknownHostException e) {
            ShellyApiException ea = new ShellyApiException(e);
            logger.debug("{}: CoAP Exception {}", thingName, e.toString());
            throw ea;
        }
    }

    public boolean isStarted() {
        return started;
    }

    /**
     * Process an inbound Response (or mapped Request): decode Coap options. handle discovery result or status updates
     *
     * @param response The Response packet
     */
    @Override
    public void processResponse(@Nullable Response response) {
        if (response == null) {
            return; // other device instance
        }
        String ip = response.getSourceContext().getPeerAddress().toString();
        if (!ip.contains(config.deviceIp)) {
            return;
        }

        String payload = "";
        String devId = "";
        String uri = "";
        // int validity = 0;
        int serial = 0;
        try {
            logger.debug("{}: CoIoT Message from {}: {}", thingName, response.getSourceContext().getPeerAddress(),
                    response.toString());
            if (response.isCanceled() || response.isDuplicate() || response.isRejected()) {
                logger.debug("{} ({}): Packet was canceled, rejected or is a duplicate -> discard", thingName, devId);
                return;
            }

            if (response.getCode() == ResponseCode.CONTENT) {
                payload = response.getPayloadString();
                List<Option> options = response.getOptions().asSortedList();
                int i = 0;
                while (i < options.size()) {
                    Option opt = options.get(i);
                    switch (opt.getNumber()) {
                        case OptionNumberRegistry.URI_PATH:
                            uri = COLOIT_URI_BASE + opt.getStringValue();
                            break;
                        case COIOT_OPTION_GLOBAL_DEVID:
                            devId = opt.getStringValue();
                            break;
                        case COIOT_OPTION_STATUS_VALIDITY:
                            // validity = o.getIntegerValue();
                            break;
                        case COIOT_OPTION_STATUS_SERIAL:
                            serial = opt.getIntegerValue();
                            if (serial == lastSerial) {
                                // As per specification the serial changes when any sensor data has changed. The App
                                // should ignore any updates with the same serial. However, as we have seen with the
                                // Shelly HT and Shelly 4 Pro this is not always the case. The device comes up with an
                                // status packet having the same serial, but new payload information.
                                // Work Around: Packet will only be ignored when Serial AND Payload are the same as last
                                // time
                                if (!lastPayload.isEmpty() && !lastPayload.equals(payload)) {
                                    logger.debug(
                                            "{}: Duplicate serial {} will be processed, because payload is different: {} vs. {}",
                                            thingName, serial, payload, lastPayload);
                                    break;
                                }
                                logger.debug("{}: Serial {} was already processed, ignore update; payload={}",
                                        thingName, serial, payload);
                                return;
                            }
                            break;
                        default:
                            logger.debug("{} ({}): COAP option {} with value {} skipped", thingName, devId,
                                    opt.getNumber(), opt.getValue());
                    }
                    i++;
                }

                // If we received a CoAP message successful the thing must be online
                th.setThingOnline();

                if (uri.equalsIgnoreCase(COLOIT_URI_DEVDESC) || (uri.isEmpty() && payload.contains(COIOT_TAG_BLK))) {
                    handleDeviceDescription(devId, payload);
                } else if (uri.equalsIgnoreCase(COLOIT_URI_DEVSTATUS)
                        || (uri.isEmpty() && payload.contains(COIOT_TAG_GENERIC))) {
                    handleStatusUpdate(devId, payload, serial);
                }
            } else {
                // error handling
                logger.debug("{}: Unknown Response Code {} received, payload={}", thingName, response.getCode(),
                        response.getPayloadString());
            }

            if (!discovering) {
                // Observe Status Updates
                reqStatus = sendRequest(reqStatus, config.deviceIp, COLOIT_URI_DEVSTATUS, Type.NON);
                discovering = true;
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.debug("{}: Unable to process CoIoT Message for payload={}{}", thingName, payload, e.toString());
            resetSerial();
        }
    }

    /**
     * Process a CoIoT device description message. This includes definitions on device units (Relay0, Relay1, Sensors
     * etc.) as well as a definition of sensors and actors. This information needs to be stored allowing to map ids from
     * status updates to the device units and matching the correct thing channel.
     *
     * @param payload Device desciption in JSon format, example:
     *            {"blk":[{"I":0,"D":"Relay0"}],"sen":[{"I":112,"T":"Switch","R":"0/1","L":0}],"act":[{"I":211,"D":"Switch","L":0,"P":[{"I":2011,"D":"ToState","R":"0/1"}]}]}
     *
     * @param devId The device id reported in the CoIoT message.
     */
    private void handleDeviceDescription(String devId, String payload) {
        // Device description: payload = StringUtils.substringBefore(payload, "}]}]}") + "}]}]}";
        logger.debug("{}: CoIoT Device Description for {}: {}", thingName, devId, payload);

        // Decode Json
        CoIotDevDescription descr = gson.fromJson(payload, CoIotDevDescription.class);

        int i;
        for (i = 0; i < descr.blk.size(); i++) {
            CoIotDescrBlk blk = descr.blk.get(i);
            logger.debug("{}:    id={}: {}", thingName, blk.id, blk.desc);
            if (!blockMap.containsKey(blk.id)) {
                blockMap.put(blk.id, blk);
            } else {
                blockMap.replace(blk.id, blk);
            }
            if ((blk.type != null) && !blk.type.isEmpty()) {
                // in fact it is a sen entry - that's vioaling the Spec
                logger.trace("{}:    fix: auto-create sensor definition for id {}/{}!", thingName, blk.id, blk.desc);
                CoIotDescrSen sen = new CoIotDescrSen();
                sen.id = blk.id;
                sen.desc = blk.desc;
                sen.type = blk.type;
                sen.range = blk.range;
                sen.links = blk.links;
                addSensor(sen);
            }
        }
        logger.debug("{}: Adding {} sensor definitions", thingName, descr.sen.size());
        if (descr.sen != null) {
            for (i = 0; i < descr.sen.size(); i++) {
                addSensor(descr.sen.get(i));
            }
        }

        // Save to thing properties
        th.updateProperties(PROPERTY_COAP_DESCR, payload);
    }

    private void addSensor(CoIotDescrSen sen) {
        logger.debug("{}:    id {}: {}, Type={}, Range={}, Links={}", thingName, sen.id, sen.desc, sen.type, sen.range,
                sen.links);
        try {
            CoIotDescrSen fixed = fixDescription(sen);
            if (!sensorMap.containsKey(fixed.id)) {
                sensorMap.put(sen.id, fixed);
            } else {
                sensorMap.replace(sen.id, fixed);
            }
        } catch (NullPointerException e) { // depending on firmware release the Coap device description is buggy
            logger.debug("{}: Unable to decode sensor definition -> skip {}", thingName, e.toString());
        }
    }

    /**
     * Process CoIoT status update message. If a status update is received, but the device description has not been
     * received yet a GET is send to query device description.
     *
     * @param devId device id included in the status packet
     * @param payload Coap payload (Json format), example: {"G":[[0,112,0]]}
     * @param serial Serial for this request. If this the the same as last serial
     *            the update was already sent and processed so this one gets
     *            ignored.
     */
    private void handleStatusUpdate(String devId, String payload, int serial) {
        logger.debug("{}: CoIoT Sensor data {}", thingName, payload);
        if (blockMap.size() == 0) {
            // send discovery packet
            resetSerial();
            reqDescription = sendRequest(reqDescription, config.deviceIp, COLOIT_URI_DEVDESC, Type.CON);

            // try to uses description from last initialization
            String savedDescr = th.getProperty(PROPERTY_COAP_DESCR);
            if (savedDescr.isEmpty()) {
                logger.debug("{}: Device description not yet received, trigger auto-initialization", thingName);
                return;
            }

            // simulate received device description to create element table
            handleDeviceDescription(devId, savedDescr);
            logger.debug("{}: Device description for {} restored: {}", thingName, devId, savedDescr);
        }

        // Parse Json,
        CoIotGenericSensorList list = gson.fromJson(payload, CoIotGenericSensorList.class);
        Map<String, State> updates = new TreeMap<String, State>();

        if (list.generic == null) {
            logger.debug("{}: Sensor list is empty! Payload: {}", devId, payload);
            return;
        }

        ShellyDeviceProfile profile = th.getProfile();

        if (!profile.isInitialized()) {
            logger.debug("{}: Thing not initialized yet, skip update (ID={})", thingName, devId);
            th.requestUpdates(1, true);
            return;
        }

        logger.debug("{}: {} status updates received", thingName, new Integer(list.generic.size()).toString());
        lastBrightness = -1.0;
        for (int i = 0; i < list.generic.size(); i++) {
            try {
                CoIotSensor s = list.generic.get(i);
                if (!sensorMap.containsKey(s.id)) {
                    logger.debug("{}: Invalid index in sensor description: {}", thingName, i);
                    continue;
                }
                CoIotDescrSen sen = sensorMap.get(s.id);
                // find matching sensor definition from device description, use the Link ID as index
                sen = fixDescription(sen);
                if (!blockMap.containsKey(sen.links)) {
                    logger.debug("{}: Invalid CoAP description: sen.links({}", thingName, getString(sen.links));
                    continue;
                }
                CoIotDescrBlk element = blockMap.get(sen.links);
                logger.debug("{}:  Sensor value[{}]: id={}, Value={} ({}, Type={}, Range={}, Link={}: {})", thingName,
                        i, s.id, s.value, sen.desc, sen.type, sen.range, sen.links, element.desc);

                // Process status information and convert into channel updates
                Integer rIndex = Integer.parseInt(sen.links) + 1;
                String rGroup = profile.numRelays <= 1 ? CHANNEL_GROUP_RELAY_CONTROL
                        : CHANNEL_GROUP_RELAY_CONTROL + rIndex;

                switch (sen.type.toLowerCase()) /* CoIoT_STypes.valueOf(sen.T) */ {
                    case "b" /* BatteryLevel */:
                        updateChannel(updates, CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LEVEL,
                                toQuantityType(s.value, DIGITS_PERCENT, SmartHomeUnits.PERCENT));
                        break;
                    case "t" /* Temperature */:
                        Double value = getDouble(s.value);
                        switch (sen.desc.toLowerCase()) {
                            case "temperature":
                                if ((profile.settings.temperatureUnits != null)
                                        && profile.settings.temperatureUnits.equalsIgnoreCase("F")) {
                                    value = (getDouble(s.value) - 32) * (0.5556);
                                }
                                updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP,
                                        toQuantityType(value, DIGITS_TEMP, SIUnits.CELSIUS));
                                break;

                            case "external_temperature": // Shelly 1/1PM externaö temp sensors
                                logger.debug("{}: Update external sensor from Coap update", thingName);
                                int idx = getExtTempId(sen.id);
                                if (idx > 0) {
                                    updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP + idx,
                                            toQuantityType(value, DIGITS_TEMP, SIUnits.CELSIUS));
                                }
                                break;
                            case "temperature f":
                                value = (getDouble(s.value) - 32) * (0.5556);
                            case "temperature c":
                                // Device temperature
                                updateChannel(updates, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ITEMP,
                                        toQuantityType(value, DIGITS_TEMP, SIUnits.CELSIUS));
                                break;
                            default:
                                // Regular sensor temp (H&T)
                                updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP,
                                        toQuantityType(value, DIGITS_TEMP, SIUnits.CELSIUS));
                        }
                        break;
                    case "h" /* Humidity */:
                        updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_HUM,
                                toQuantityType(s.value, DIGITS_PERCENT, SmartHomeUnits.PERCENT));
                        break;
                    case "m" /* Motion */:
                        updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION,
                                s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                        break;
                    case "l" /* Luminosity */:
                        updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_LUX,
                                toQuantityType(s.value, DIGITS_LUX, SmartHomeUnits.LUX));
                        break;
                    case "p" /* Power/Watt */:
                        String mGroup = profile.numMeters == 1 ? CHANNEL_GROUP_METER : CHANNEL_GROUP_METER + rIndex;
                        updateChannel(updates, mGroup, CHANNEL_METER_CURRENTWATTS,
                                toQuantityType(s.value, DIGITS_WATT, SmartHomeUnits.WATT));
                        if (profile.isEMeter) {
                            updateChannel(updates, mGroup, CHANNEL_LAST_UPDATE, getTimestamp());
                        }
                        break;

                    case "s" /* CatchAll */:
                        String senValue = sen.desc.toLowerCase();
                        switch (senValue) {
                            case "state":
                            case "output":
                                updatePower(profile, updates, rIndex, sen, s);
                                break;
                            case "overtemp":
                                if (s.value == 1) {
                                    th.postEvent(ALARM_TYPE_OVERTEMP, true);
                                }
                                break;
                            case "energy counter 0 [w-min]":
                            case "e cnt 0 [w-min]": // 4 Pro
                                updateChannel(updates, rGroup, CHANNEL_METER_LASTMIN1,
                                        toQuantityType(s.value, DIGITS_WATT, SmartHomeUnits.WATT));
                                break;
                            case "energy counter 1 [w-min]":
                            case "e cnt 1 [w-min]": // 4 Pro
                                updateChannel(updates, rGroup, CHANNEL_METER_LASTMIN2,
                                        toQuantityType(s.value, DIGITS_WATT, SmartHomeUnits.WATT));
                                break;
                            case "energy counter 2 [w-min]":
                            case "e cnt 2 [w-min]": // 4 Pro
                                updateChannel(updates, rGroup, CHANNEL_METER_LASTMIN3,
                                        toQuantityType(s.value, DIGITS_WATT, SmartHomeUnits.WATT));
                                break;
                            case "energy counter total [w-h]": // EM3 reports W/h
                            case "energy counter total [w-min]":
                            case "e cnt total [w-min]": // 4 Pro
                                updateChannel(updates, rGroup, CHANNEL_METER_TOTALKWH,
                                        toQuantityType(profile.isEMeter ? s.value / 1000 : s.value / 60 / 1000,
                                                DIGITS_KWH, SmartHomeUnits.KILOWATT_HOUR));
                                break;
                            case "voltage":
                                updateChannel(updates, rGroup, CHANNEL_EMETER_VOLTAGE,
                                        toQuantityType(getDouble(s.value), DIGITS_VOLT, SmartHomeUnits.VOLT));
                                break;
                            case "current":
                                updateChannel(updates, rGroup, CHANNEL_EMETER_CURRENT,
                                        toQuantityType(getDouble(s.value), DIGITS_VOLT, SmartHomeUnits.AMPERE));
                                break;
                            case "pf":
                                updateChannel(updates, rGroup, CHANNEL_EMETER_PFACTOR, getDecimal(s.value));
                                break;
                            case "position":
                                // work around: Roller reports 101% instead max 100
                                double pos = Math.max(SHELLY_MIN_ROLLER_POS, Math.min(s.value, SHELLY_MAX_ROLLER_POS));
                                updateChannel(updates, CHANNEL_GROUP_ROL_CONTROL, CHANNEL_ROL_CONTROL_CONTROL,
                                        toQuantityType(SHELLY_MAX_ROLLER_POS - pos, SmartHomeUnits.PERCENT));
                                updateChannel(updates, CHANNEL_GROUP_ROL_CONTROL, CHANNEL_ROL_CONTROL_POS,
                                        toQuantityType(pos, SmartHomeUnits.PERCENT));
                                break;
                            case "input":
                                int idx = getSensorNumber("Input", sen.id);
                                if (idx > 0) {
                                    int r = idx - 1;
                                    String iGroup = rGroup;
                                    String iChannel = CHANNEL_INPUT;
                                    if (profile.isDimmer || profile.isRoller) {
                                        // Dimmer and Roller things have 2 inputs
                                        iChannel = CHANNEL_INPUT + String.valueOf(idx);
                                    } else {
                                        // Device has 1 input per relay: 0=off, 1+2 depend on switch mode
                                        iGroup = profile.numRelays <= 1 ? CHANNEL_GROUP_RELAY_CONTROL
                                                : CHANNEL_GROUP_RELAY_CONTROL + idx;
                                    }

                                    if ((profile.settings.relays != null) && (r >= 0)
                                            && (r < profile.settings.relays.size())) {
                                        ShellySettingsRelay relay = profile.settings.relays.get(r);
                                        if ((relay != null) && (s.value != 0)
                                                && (relay.btnType.equalsIgnoreCase(SHELLY_BTNT_MOMENTARY)
                                                        || relay.btnType.equalsIgnoreCase(SHELLY_BTNT_DETACHED))) {
                                            th.postEvent(s.value == 1 ? EVENT_TYPE_SHORTPUSH : EVENT_TYPE_LONGPUSH,
                                                    true);
                                        }
                                    }
                                    updateChannel(updates, iGroup, iChannel,
                                            s.value == 0 ? OnOffType.OFF : OnOffType.ON);
                                }
                                break;

                            case "flood":
                                updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_FLOOD,
                                        s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                                break;
                            case "brightness": // Dimmer
                                updatePower(profile, updates, rIndex, sen, s);
                                break;
                            case "charger": // Sense
                                updateChannel(updates, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_CHARGER,
                                        s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                                break;

                            // RGBW2/Bulb
                            case "red":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_RED,
                                        ShellyColorUtils.toPercent((int) s.value));
                                break;
                            case "green":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_GREEN,
                                        ShellyColorUtils.toPercent((int) s.value));
                                break;
                            case "blue":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_BLUE,
                                        ShellyColorUtils.toPercent((int) s.value));
                                break;
                            case "white":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_WHITE,
                                        ShellyColorUtils.toPercent((int) s.value));
                                break;
                            case "gain":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_GAIN,
                                        ShellyColorUtils.toPercent((int) s.value, SHELLY_MIN_GAIN, SHELLY_MAX_GAIN));
                                break;
                            case "temp": // Shelly Bulb
                            case "colortemperature": // Shelly Duo
                                updateChannel(updates,
                                        profile.inColor ? CHANNEL_GROUP_COLOR_CONTROL : CHANNEL_GROUP_WHITE_CONTROL,
                                        CHANNEL_COLOR_TEMP,
                                        ShellyColorUtils.toPercent((int) s.value, profile.minTemp, profile.maxTemp));
                                break;
                            default:
                                logger.debug("{}: Update for unknown sensor type {}/{} received", thingName, sen.type,
                                        sen.desc);
                        }
                        break;
                    default:
                        logger.debug("{}: Sensor data for type {} not processed, value={}", thingName, sen.type,
                                s.value);
                }
            } catch (IllegalArgumentException | NullPointerException | ArrayIndexOutOfBoundsException e) {
                // even the processing of one value failed we continue with the next one (sometimes this is caused by
                // buggy formats provided by the device
                logger.debug("{}: Unable to process data from sensor[{}]: Dev={}{}", thingName, i, devId, e.toString());
            }
        }

        if (updates.size() > 0)

        {
            logger.debug("{}: Process {} CoIoT channel updates", thingName, updates.size());
            int i = 0;
            for (Map.Entry<String, State> u : updates.entrySet()) {
                logger.debug("{}:  Update[{}] channel {}, value={} (type {})", thingName, i, u.getKey(), u.getValue(),
                        u.getValue().getClass());
                th.updateChannel(u.getKey(), u.getValue(), true);
                i++;
            }

            // Old firmware release are lacking various status values, which are not updated using CoIoT.
            // In this case we keep a refresh so it gets polled using REST. Beginning with Firmware 1.6 most
            // of the values are available so in thise case we could skip an extra poll to reduce load.
            if (coapTriggersRefresh && (th.scheduledUpdates == 0)) {
                th.requestUpdates(1, false);
            }
        }

        // Remeber serial, new packets with same serial will be ignored
        lastSerial = serial;
        lastPayload = payload;
    }

    private void updatePower(ShellyDeviceProfile profile, Map<String, State> updates, Integer id, CoIotDescrSen sen,
            CoIotSensor s) {
        String group = "";
        String channel = "";
        if (profile.isLight || profile.isDimmer) {
            if (profile.isBulb || profile.inColor) {
                group = CHANNEL_GROUP_LIGHT_CONTROL;
                channel = CHANNEL_LIGHT_POWER;
            } else if (profile.isDuo) {
                group = CHANNEL_GROUP_WHITE_CONTROL;
                channel = CHANNEL_BRIGHTNESS;
            } else if (profile.isDimmer) {
                group = CHANNEL_GROUP_RELAY_CONTROL;
                channel = CHANNEL_BRIGHTNESS;
            } else {
                // RGBW2
                group = CHANNEL_GROUP_LIGHT_CHANNEL + id;
                channel = CHANNEL_BRIGHTNESS;
            }

            if (sen.desc.equalsIgnoreCase("brightness")) {
                lastBrightness = s.value;
            } else {
                OnOffType state = s.value == 1 ? OnOffType.ON : OnOffType.OFF;
                updateChannel(updates, group, channel + "$Switch", state);
                if (lastBrightness < 0.0) {
                    // get current value from channel
                    QuantityType<?> last = (QuantityType<?>) th.getChannelValue(group, channel + "$Value");
                    lastBrightness = last != null ? last.doubleValue() : 50;
                }
                updateChannel(updates, group, channel + "$Value", toQuantityType(
                        state == OnOffType.ON ? lastBrightness : 0, DIGITS_NONE, SmartHomeUnits.PERCENT));
                lastBrightness = -1.0;
            }
        } else if (profile.hasRelays) {
            group = profile.numRelays <= 1 ? CHANNEL_GROUP_RELAY_CONTROL : CHANNEL_GROUP_RELAY_CONTROL + id;
            updateChannel(updates, group, CHANNEL_OUTPUT, s.value == 1 ? OnOffType.ON : OnOffType.OFF);
        } else if (profile.isSensor) {
            // Sensor state
            if (profile.isDW) { // Door Window has item type Contact
                updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_STATE,
                        s.value != 0 ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
            } else {
                updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_STATE,
                        s.value == 1 ? OnOffType.ON : OnOffType.OFF);
            }
        }
    }

    private boolean updateChannel(Map<String, State> updates, String group, String channel, State value) {
        State v = (State) th.getChannelValue(group, channel);
        if ((v != null) && v.equals(value)) {
            return false;
        }
        logger.trace("{}: Updating channel {}.{} from CoIoT, new value={}", thingName, group, channel, value);
        updates.put(mkChannelId(group, channel), value);
        return true;

    }

    /**
     * Work around to fix inconsistent sensor types and description. Shelly not uses always the same coding for sen.T
     * and sen.D - this helps to unify the format and simplifies processing
     *
     * @param sen Semsor description
     * @return updated sen
     */
    private CoIotDescrSen fixDescription(CoIotDescrSen sen) {
        // Shelly1: reports null descr+type "Switch" -> map to S
        // Shelly1PM: reports null descr+type "Overtemp" -> map to O
        // Shelly1PM: reports null descr+type "W" -> add description
        // Shelly1PM: reports temp senmsors without desc -> add description
        // Shelly Dimmer: sensors are reported without descriptions -> map to S
        // SHelly Sense: multiple issues: Description should not be lower case, invalid type for Motion and Battery
        // Shelly Sense: Battery is reported with Desc "battery", but type "H" instead of "B"
        // Shelly Sense: Motion is reported with Desc "battery", but type "H" instead of "B"
        // Shelly Bulb: Colors are coded with Type="Red" etc. rather than Type="S" and color as Descr
        if (sen.desc == null) {
            sen.desc = "";
        }

        switch (sen.type.toLowerCase()) {
            case "w": // old devices/firmware releases use "W", new ones "P"
                sen.type = "P";
                sen.desc = "Power";
                break;
            case "tc":
                sen.type = "T";
                sen.desc = "Temperature C";
                break;
            case "tf":
                sen.type = "T";
                sen.desc = "Temperature F";
                break;
            case "overtemp":
                sen.type = "S";
                sen.desc = "Overtemp";
                break;
            case "relay0":
            case "switch":
            case "vswitch":
                sen.type = "S";
                sen.desc = "State";
                break;
        }

        switch (sen.desc.toLowerCase()) {
            case "motion": // fix acc to spec it's T=M
                sen.type = "M";
                sen.desc = "Motion";
                break;
            case "battery": // fix: type is B not H
                sen.type = "B";
                sen.desc = "Battery";
                break;
            case "overtemp":
                sen.type = "S";
                sen.desc = "Overtemp";
                break;
            case "relay0":
            case "switch":
            case "vswitch":
                sen.type = "S";
                sen.desc = "State";
                break;
        }

        if (sen.desc.isEmpty()) {
            switch (sen.type.toLowerCase()) {
                case "p":
                    sen.desc = "Power";
                    break;
                case "T":
                    sen.desc = "Temperature";
                    break;
                case "input":
                    sen.type = "S";
                    sen.desc = "Input";
                    break;
                case "output":
                    sen.type = "S";
                    sen.desc = "Output";
                    break;
                case "brightness":
                    sen.type = "S";
                    sen.desc = "Brightness";
                    break;

                case "red":
                case "green":
                case "blue":
                case "white":
                case "gain":
                case "temp": // Bulb: Color temperature
                    sen.desc = sen.type;
                    sen.type = "S";
                    break;

                case "vswitch":
                    // it seems that Shelly tends to break their own spec: T is the description and D is no longer
                    // included
                    // -> map D to sen.T and set CatchAll for T
                    sen.desc = sen.type;
                    sen.type = "S";
                    break;

                // Default: set no description
                // (there are no T values defined in the CoIoT spec)
                case "tostate":
                default:
                    sen.desc = "";
            }
        }
        return sen;
    }

    /**
     * Send a new request (Discovery to get Device Description). Before a pending
     * request will be canceled.
     *
     * @param request The current request (this will be canceled an a new one will
     *            be created)
     * @param ipAddress Device's IP address
     * @param uri The URI we are calling (CoIoT = /cit/d or /cit/s)
     * @param con true: send as CON, false: send as NON
     * @return new packet
     */
    private Request sendRequest(@Nullable Request request, String ipAddress, String uri, Type con) {
        if ((request != null) && !request.isCanceled()) {
            request.cancel();
        }

        resetSerial();
        return newRequest(ipAddress, uri, con).send();
    }

    /**
     * Allocate a new Request structure. A message observer will be added to get the
     * callback when a response has been received.
     *
     * @param ipAddress IP address of the device
     * @param uri URI to be addressed
     * @param uri The URI we are calling (CoIoT = /cit/d or /cit/s)
     * @param con true: send as CON, false: send as NON
     * @return new packet
     */

    private Request newRequest(String ipAddress, String uri, Type con) {
        // We need to build our own Request to set an empty Token
        Request request = new Request(Code.GET, con);
        request.setURI(completeUrl(ipAddress, uri));
        request.setToken(EMPTY_BYTE);
        request.addMessageObserver(new MessageObserverAdapter() {
            @Override
            public void onResponse(@Nullable Response response) {
                processResponse(response);
            }

            @Override
            public void onCancel() {
                logger.debug("{}: Coap Request was canceled", thingName);
            }

            @Override
            public void onTimeout() {
                logger.debug("{}: Coap Request timed out", thingName);
            }

        });
        return request;

    }

    private void resetSerial() {
        lastSerial = -1;
        lastPayload = "";
    }

    /**
     * Find index of Input id, which is required to map to channel name
     *
     * @param sensorId The id from the sensor update
     * @return Index of found entry (+1 will be the suffix for the channel name) or null if sensorId is not found
     */
    private int getSensorNumber(String sensorName, String sensorId) {
        int idx = 0;
        for (Map.Entry<String, CoIotDescrSen> se : sensorMap.entrySet()) {
            CoIotDescrSen sen = se.getValue();
            if (sen.desc.equalsIgnoreCase(sensorName)) {
                idx++; // iterate from input1..2..n
            }
            if (sen.id.equalsIgnoreCase(sensorId) && blockMap.containsKey(sen.links)) {
                CoIotDescrBlk blk = blockMap.get(sen.links);
                if (StringUtils.substring(blk.desc, 5).equalsIgnoreCase("Relay")) {
                    idx = Integer.parseInt(StringUtils.substringAfter(blk.desc, "Relay"));
                }
                logger.trace("{}:    map sensor {}{} to index {}", thingName, sensorName, sensorId, idx);
                return idx;
            }
        }
        logger.debug("{}: sensorId {} not found in sensorMap!", thingName, sensorId);
        return -1;
    }

    private int getExtTempId(String sensorId) {
        int idx = 0;
        for (Map.Entry<String, CoIotDescrSen> se : sensorMap.entrySet()) {
            CoIotDescrSen sen = se.getValue();
            if (sen.desc.equalsIgnoreCase("External_temperature")) {
                idx++; // iterate from temperature1..2..n
            }
            if (sen.id.equalsIgnoreCase(sensorId)) {
                logger.trace("{}:    map sensir id {} to temperature{} channel", thingName, sensorId, idx);
                return idx;
            }
        }
        logger.debug("{}: sensorId {} not found in sensorMap!", thingName, sensorId);
        return -1;
    }

    /**
     * Cancel pending requests and shutdown the client
     */
    public void stop() {
        if (started) {
            logger.debug("{}: Stop CoapHandler instance", thingName);
            if (!reqDescription.isCanceled()) {
                reqDescription.cancel();
            }
            if (!reqStatus.isCanceled()) {
                reqStatus.cancel();
            }
            if (statusClient != null) {
                statusClient.shutdown();
                statusClient = null;
            }
            coapServer.removeListener(this);
        }
        started = false;
    }

    public void dispose() {
        stop();
    }

    private static String completeUrl(String ipAddress, String uri) {
        return "coap://" + ipAddress + ":" + COIOT_PORT + uri;

    }
}
