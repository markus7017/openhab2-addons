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
package org.openhab.binding.carnet.internal.services;

import static org.openhab.binding.carnet.internal.CarNetBindingConstants.*;
import static org.openhab.binding.carnet.internal.CarNetUtils.getString;
import static org.openhab.binding.carnet.internal.api.CarNetApiConstants.CNAPI_SERVICE_VEHICLE_STATUS_REPORT;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import javax.measure.IncommensurableException;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.api.CarNetApi;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleStatus;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleStatus.CNStoredVehicleDataResponse.CNVehicleData.CNStatusData;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleStatus.CNStoredVehicleDataResponse.CNVehicleData.CNStatusData.CNStatusField;
import org.openhab.binding.carnet.internal.handler.CarNetVehicleHandler;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper.ChannelIdMapEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CarNetVehicleServiceStatus} implements fetching the basic vehicle status data.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetVehicleServiceStatus extends CarNetVehicleBaseService {
    private final Logger logger = LoggerFactory.getLogger(CarNetVehicleServiceStatus.class);

    public CarNetVehicleServiceStatus(CarNetVehicleHandler thingHandler, CarNetApi api) {
        super(thingHandler, api);
        serviceId = CNAPI_SERVICE_VEHICLE_STATUS_REPORT;
    }

    @Override
    public boolean createChannels(Map<String, ChannelIdMapEntry> channels) throws CarNetException {
        boolean updated = false;

        // Try to query status information from vehicle
        CarNetVehicleStatus status = api.getVehicleStatus();
        for (CNStatusData data : status.storedVehicleDataResponse.vehicleData.data) {
            for (CNStatusField field : data.fields) {
                try {
                    ChannelIdMapEntry definition = idMapper.find(field.id);
                    if (definition != null) {
                        logger.info("{}: {}={}{} (channel {}#{})", thingId, definition.symbolicName,
                                getString(field.value), getString(field.unit), definition.groupName,
                                definition.channelName);
                        if (!definition.channelName.isEmpty()) {
                            if (!definition.channelName.startsWith(CHANNEL_GROUP_TIRES) || !field.value.contains("1")) {
                                if (!channels.containsKey(definition.id)) {
                                    channels.put(definition.id, definition);
                                    updated = true;
                                }
                            }
                        }
                    } else {
                        logger.debug("{}: Unknown data field {}.{}, value={}{}", thingId, data.id, field.id,
                                field.value, getString(field.unit));
                    }
                } catch (RuntimeException e) {

                }
            }
        }

        addChannel(channels, CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION, ITEMT_STRING, null, false, true);
        addChannel(channels, CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION_STATUS, ITEMT_STRING, null, false, true);
        addChannel(channels, CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION_PENDING, ITEMT_SWITCH, null, false, true);

        return updated;
    }

    @Override
    public boolean serviceUpdate() throws CarNetException {
        // Try to query status information from vehicle
        logger.debug("{}: Get Vehicle Status", thingId);
        boolean maintenanceRequired = false; // true if any maintenance is required
        boolean vehicleLocked = true; // aggregates all lock states
        boolean windowsClosed = true; // true if all Windows are closed
        boolean tiresOk = true; // tire if all tire pressures are ok

        CarNetVehicleStatus status = api.getVehicleStatus();
        logger.debug("{}: Vehicle Status:\n{}", thingId, status);
        for (CNStatusData data : status.storedVehicleDataResponse.vehicleData.data) {
            for (CNStatusField field : data.fields) {
                ChannelIdMapEntry definition = idMapper.find(field.id);
                if (definition != null) {
                    logger.debug("{}: {}={}{} (channel {}#{})", thingId, definition.symbolicName,
                            getString(field.value), getString(field.unit), getString(definition.groupName),
                            getString(definition.channelName));
                    if (!definition.channelName.isEmpty()) {
                        Channel channel = thingHandler.getThing()
                                .getChannel(definition.groupName + "#" + definition.channelName);
                        if (channel != null) {
                            logger.debug("Updading channel {} with value {}", channel.getUID(), getString(field.value));
                            switch (definition.itemType) {
                                case ITEMT_SWITCH:
                                    updateSwitchChannel(channel, definition, field);
                                    break;
                                case ITEMT_STRING:
                                    thingHandler.updateChannel(channel, new StringType(getString(field.value)));
                                    break;
                                case ITEMT_NUMBER:
                                case ITEMT_PERCENT:
                                default:
                                    updateNumberChannel(channel, definition, field);
                            }
                        } else {
                            logger.debug("Channel {}#{} not found", definition.groupName, definition.channelName);
                        }

                        if ((field.value != null) && !field.value.isEmpty()) {
                            vehicleLocked &= checkLocked(field, definition);
                            maintenanceRequired |= checkMaintenance(field, definition);
                            tiresOk &= checkTires(field, definition);
                            windowsClosed &= checkWindows(field, definition);
                        }
                    }
                } else {
                    logger.debug("{}: Unknown data field  {}.{}, value={} {}", thingId, data.id, field.id, field.value,
                            field.unit);
                }
            }
        }

        // Update aggregated status
        thingHandler.updateChannel(CHANNEL_GROUP_STATUS, CHANNEL_GENERAL_LOCKED,
                vehicleLocked ? OnOffType.ON : OnOffType.OFF);
        thingHandler.updateChannel(CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_LOCK,
                vehicleLocked ? OnOffType.ON : OnOffType.OFF);
        thingHandler.updateChannel(CHANNEL_GROUP_STATUS, CHANNEL_GENERAL_MAINTREQ,
                maintenanceRequired ? OnOffType.ON : OnOffType.OFF);
        thingHandler.updateChannel(CHANNEL_GROUP_STATUS, CHANNEL_GENERAL_TIRESOK,
                tiresOk ? OnOffType.ON : OnOffType.OFF);
        thingHandler.updateChannel(CHANNEL_GROUP_STATUS, CHANNEL_GENERAL_WINCLOSED,
                windowsClosed ? OnOffType.ON : OnOffType.OFF);

        return true;
    }

    private boolean checkMaintenance(CNStatusField field, ChannelIdMapEntry definition) {
        if (definition.symbolicName.contains("MAINT_ALARM") && field.value.equals("1")) {
            // MAINT_ALARM_INSPECTION+MAINT_ALARM_OIL_CHANGE + MAINT_ALARM_OIL_MINIMUM
            logger.debug("{}: Maintenance required: {} has incorrect pressure", thingId, definition.symbolicName);
            return true;
        }
        if (definition.symbolicName.contains("AD_BLUE_RANGE") && (Integer.parseInt(field.value) < 1000)) {
            logger.debug("{}: Maintenance required: Ad Blue at {} (< 1.000km)", thingId, field.value);
            return true;
        }
        return false;
    }

    private boolean checkLocked(CNStatusField field, ChannelIdMapEntry definition) {
        if (definition.symbolicName.contains("LOCK")) {
            boolean result = (definition.symbolicName.contains("LOCK2") && field.value.equals(String.valueOf(2)))
                    || (definition.symbolicName.contains("LOCK3") && field.value.equals(String.valueOf(3)));
            if (!result) {
                logger.debug("{}: Vehicle is not completetly locked: {}", thingId, definition.channelName);
                return false;
            }
        }
        return true;
    }

    private boolean checkWindows(CNStatusField field, ChannelIdMapEntry definition) {
        if (definition.symbolicName.contains("WINDOWS") && definition.symbolicName.contains("STATE")
                && !field.value.equals(String.valueOf(3))) {
            logger.debug("{}: Window {} is not closed", thingId, definition.channelName);
        }
        return true;
    }

    private boolean checkTires(CNStatusField field, ChannelIdMapEntry definition) {
        if (definition.symbolicName.contains("TIREPRESS") && definition.symbolicName.contains("CURRENT")
                && !field.value.equals(String.valueOf(1))) {
            logger.debug("{}: Tire pressure for {} is not ok", thingId, definition.channelName);
        }
        return true;
    }

    private void updateNumberChannel(Channel channel, ChannelIdMapEntry definition, CNStatusField field) {
        State state = UnDefType.UNDEF;
        String val = getString(field.value);
        if (!val.isEmpty()) {
            double value = Double.parseDouble(val);
            if (value < 0) {
                value = value * -1.0; // no egative values
            }
            BigDecimal bd = new BigDecimal(value);
            if (definition.unit != null) {
                ChannelIdMapEntry fromDef = idMapper.updateDefinition(field, definition);
                Unit<?> fromUnit = fromDef.fromUnit;
                Unit<?> toUnit = definition.unit;
                if ((fromUnit != null) && (toUnit != null) && !fromUnit.equals(toUnit)) {
                    try {
                        // Convert between units
                        bd = new BigDecimal(fromUnit.getConverterToAny(toUnit).convert(value));
                    } catch (UnconvertibleException | IncommensurableException e) {
                        logger.debug("{}: Unable to covert value", thingId, e);
                    }
                }
            }
            value = bd.setScale(2, RoundingMode.HALF_EVEN).doubleValue();
            Unit<?> unit = definition.unit;
            if (unit != null) {
                state = new QuantityType<>(value, unit);
            } else {
                state = new DecimalType(val);
            }
        }
        logger.debug("{}: Updating channel {} with {}", thingId, channel.getUID().getId(), state);
        thingHandler.updateChannel(channel, state);
    }

    private void updateSwitchChannel(Channel channel, ChannelIdMapEntry definition, CNStatusField field) {
        int value = Integer.parseInt(getString(field.value));
        boolean on;
        if (definition.symbolicName.toUpperCase().contains("STATE1_")) {
            on = value == 1; // 1=active, 0=not active
        } else if (definition.symbolicName.toUpperCase().contains("STATE2_")) {
            on = value == 2; // 3=open, 2=closed
        } else if (definition.symbolicName.toUpperCase().contains("STATE3_")
                || definition.symbolicName.toUpperCase().contains("SAFETY_")) {
            on = value == 3; // 2=open, 3=closed
        } else if (definition.symbolicName.toUpperCase().contains("LOCK2_")) {
            // mark a closed lock ON
            on = value == 2; // 2=open, 3=closed
        } else if (definition.symbolicName.toUpperCase().contains("LOCK3_")) {
            // mark a closed lock ON
            on = value == 3; // 3=open, 2=closed
        } else {
            on = value == 1;
        }

        State state = on ? OnOffType.ON : OnOffType.OFF;
        logger.debug("{}: Map value {} to state {} for channe {}, symnolicName{}", thingId, value, state,
                definition.channelName, definition.symbolicName);
        thingHandler.updateChannel(channel, state);
    }
}
