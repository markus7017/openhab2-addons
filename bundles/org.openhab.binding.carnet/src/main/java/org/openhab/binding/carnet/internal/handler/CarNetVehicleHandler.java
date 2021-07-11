/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.carnet.internal.handler;

import static org.openhab.binding.carnet.internal.CarNetBindingConstants.*;
import static org.openhab.binding.carnet.internal.CarNetUtils.*;
import static org.openhab.binding.carnet.internal.api.CarNetApiConstants.*;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.carnet.internal.CarNetChannelCache;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.CarNetTextResources;
import org.openhab.binding.carnet.internal.api.CarNetApiBase;
import org.openhab.binding.carnet.internal.api.CarNetApiErrorDTO;
import org.openhab.binding.carnet.internal.api.CarNetApiErrorDTO.CNErrorMessage2Details;
import org.openhab.binding.carnet.internal.api.CarNetApiListener;
import org.openhab.binding.carnet.internal.api.CarNetApiResult;
import org.openhab.binding.carnet.internal.api.CarNetIChanneldMapper;
import org.openhab.binding.carnet.internal.api.CarNetIChanneldMapper.ChannelIdMapEntry;
import org.openhab.binding.carnet.internal.api.CarNetPendingRequest;
import org.openhab.binding.carnet.internal.api.brand.CarNetBrandApiNull;
import org.openhab.binding.carnet.internal.api.services.CarNetBaseService;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceCarFinder;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceCharger;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceClimater;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceDestinations;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceGeoFenceAlerts;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceHonkFlash;
import org.openhab.binding.carnet.internal.api.services.CarNetServicePreHeat;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceRLU;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceSpeedAlerts;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceStatus;
import org.openhab.binding.carnet.internal.api.services.CarNetServiceTripData;
import org.openhab.binding.carnet.internal.config.CarNetCombinedConfig;
import org.openhab.binding.carnet.internal.config.CarNetVehicleConfiguration;
import org.openhab.binding.carnet.internal.provider.CarNetChannelTypeProvider;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CarNetVehicleHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Markus Michels - Initial contribution
 * @author Lorenzo Bernardi - Additional contribution
 *
 */
@NonNullByDefault
public class CarNetVehicleHandler extends BaseThingHandler implements CarNetDeviceListener, CarNetApiListener {
    private final Logger logger = LoggerFactory.getLogger(CarNetVehicleHandler.class);
    private final CarNetTextResources resources;
    private final CarNetIChanneldMapper idMapper;
    private final CarNetChannelTypeProvider channelTypeProvider;
    private final CarNetChannelCache cache;
    private final int cacheCount = 20;
    private final ZoneId zoneId;

    public final String thingId;
    private CarNetApiBase api = new CarNetBrandApiNull();
    private @Nullable CarNetAccountHandler accountHandler;
    private @Nullable ScheduledFuture<?> pollingJob;
    private int updateCounter = 0;
    private int skipCount = 1;
    private boolean forceUpdate = false; // true: update status on next polling cycle
    private boolean requestStatus = false; // true: request update from vehicle
    private boolean channelsCreated = false;
    private boolean testData = false;
    private boolean stopping = false;

    private Map<String, CarNetBaseService> services = new LinkedHashMap<>();
    private CarNetCombinedConfig config = new CarNetCombinedConfig();

    public CarNetVehicleHandler(Thing thing, CarNetTextResources resources, ZoneId zoneId,
            CarNetIChanneldMapper idMapper, CarNetChannelTypeProvider channelTypeProvider) throws CarNetException {
        super(thing);

        this.thingId = getThing().getUID().getId();
        this.resources = resources;
        this.idMapper = idMapper;
        this.channelTypeProvider = channelTypeProvider;
        this.zoneId = zoneId;
        this.cache = new CarNetChannelCache(this, thingId);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing!");
        updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "Initializing");
        scheduler.schedule(() -> {
            // Register listener and wait for account being ONLINE
            CarNetAccountHandler handler = null;
            Bridge bridge = getBridge();
            if (bridge != null) {
                handler = (CarNetAccountHandler) bridge.getHandler();
            }
            if ((handler == null)) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                        "Account Thing is not initialized!");
                return;
            }
            accountHandler = handler;
            config = handler.getCombinedConfig();
            config.vehicle = getConfigAs(CarNetVehicleConfiguration.class);
            api = handler.createApi(config, this);

            handler.registerListener(this);
            setupPollingJob();

            if (config.vehicle.enableAddressLookup) {
                logger.info(
                        "{}: Reverse address lookup based on vehicle's geo position is enabled (using OpenStreetMap)",
                        thingId);
            }
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * (re-)initialize the thing
     *
     * @return true=successful
     */
    boolean initializeThing() {
        boolean successful = true;
        String error = "";
        channelsCreated = false;
        requestStatus = false;

        try {
            CarNetAccountHandler handler = accountHandler;
            if (handler == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                        "Account Handler not initialized!");
                return false;
            }

            config = handler.getCombinedConfig();
            config.vehicle = getConfigAs(CarNetVehicleConfiguration.class);
            if (config.vehicle.vin.isEmpty()) {
                Map<String, String> properties = getThing().getProperties();
                String vin = properties.get(PROPERTY_VIN);
                if (vin != null) {
                    // migrate config
                    config.vehicle.vin = vin;
                    Configuration thingConfig = this.editConfiguration();
                    thingConfig.put(PROPERTY_VIN, vin);
                    this.updateConfiguration(thingConfig);
                }
            }
            if (config.vehicle.vin.isEmpty()) {
                logger.warn("VIN not set");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "VIN not set");
                return false;
            }

            try {
                config = api.initialize(config.vehicle.vin, config);
                if (!config.pairingInfo.isPairingCompleted()) {
                    logger.warn("{}: Unable to verify pairing or pairing not completed (status {}, userId {}, code {})",
                            thingId, getString(config.pairingInfo.pairingStatus), getString(config.user.id),
                            getString(config.pairingInfo.pairingCode));
                }
            } catch (CarNetException e) {
                logger.warn("{}: Available services coould not be determined, continue with default profile", thingId);
            }

            logger.debug("{}: Active userId = {}, role = {} (securityLevel {}), status = {}, Pairing Code {}", thingId,
                    config.user.id, config.user.role, config.user.securityLevel, config.user.status,
                    config.pairingInfo.pairingCode);

            skipCount = Math.max(config.vehicle.pollingInterval * 60 / POLL_INTERVAL_SEC, 2);
            cache.clear(); // clear any cached channels

            // Create services
            registerServices();

            // Create channels
            if (!channelsCreated) {
                // General channels
                Map<String, ChannelIdMapEntry> channels = new LinkedHashMap<>();
                addChannel(channels, CHANNEL_GROUP_STATUS, CHANNEL_GENERAL_LOCKED, ITEMT_SWITCH, null, false, true);
                addChannel(channels, CHANNEL_GROUP_STATUS, CHANNEL_GENERAL_MAINTREQ, ITEMT_SWITCH, null, false, true);
                addChannel(channels, CHANNEL_GROUP_STATUS, CHANNEL_GENERAL_WINCLOSED, ITEMT_SWITCH, null, false, true);
                addChannel(channels, CHANNEL_GROUP_STATUS, CHANNEL_GENERAL_TIRESOK, ITEMT_SWITCH, null, false, true);
                addChannel(channels, CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_UPDATED, ITEMT_DATETIME, null, false, true);
                addChannel(channels, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_UPDATE, ITEMT_SWITCH, null, false, false);

                // Add channels based on service information
                for (Map.Entry<String, CarNetBaseService> s : services.entrySet()) {
                    CarNetBaseService service = s.getValue();
                    if (!service.createChannels(channels)) {
                        logger.debug("{}: Service {} is not available, disable", thingId, service.getServiceId());
                        service.disable();
                    }
                }

                logger.debug("{}: Creating {} channels", thingId, channels.size());
                idMapper.dumpChannelDefinitions();
                createChannels(new ArrayList<>(channels.values()));
                channelsCreated = true;
            }
        } catch (CarNetException e) {
            CarNetApiErrorDTO res = e.getApiResult().getApiError();
            if (res.description.contains("disabled ")) {
                // Status service in the vehicle is disabled
                String message = "Status service is disabled, check data privacy settings in MMI: " + res;
                logger.debug("{}: {}", thingId, message);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
                return false;
            }

            successful = false;
            error = getError(e);
        } catch (RuntimeException e) {
            error = "General Error: " + getString(e.getMessage());
            logger.warn("{}: {}", thingId, error, e);
        }

        if (!error.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error);
        }
        return successful;
    }

    /**
     * Brigde status changed
     */
    @Override
    public void stateChanged(ThingStatus status, ThingStatusDetail detail, String message) {
        forceUpdate = true;
        cache.clear();
    }

    @Override
    public void informationUpdate(@Nullable List<CarNetVehicleInformation> vehicleList) {
        forceUpdate = true;
        channelsCreated = false;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            String channelId = channelUID.getId();
            State value = cache.getValue(channelId);
            if (value != UnDefType.NULL) {
                updateState(channelId, value);
            }
            return;
        }

        ThingStatus s = getThing().getStatus();
        if ((s == ThingStatus.INITIALIZING) || (s == ThingStatus.UNKNOWN)) {
            logger.info("{}: Thing not yet fully initialized, command ignored", thingId);
            forceUpdate = true;
            return;
        }

        String channelId = channelUID.getIdWithoutGroup();
        String error = "";
        boolean sendOffOnError = false;
        String action = "";
        String actionStatus = "";
        boolean switchOn = (command instanceof OnOffType) && (OnOffType) command == OnOffType.ON;
        logger.debug("{}: Channel {} received command {}", thingId, channelId, command);
        try {
            switch (channelId) {
                case CHANNEL_CONTROL_UPDATE:
                    if (command == OnOffType.ON) {
                        // trigger update poll, but also request vehicle to update status
                        requestStatus = true;
                        forceUpdate = true;
                    }
                    break;
                case CHANNEL_CONTROL_LOCK:
                    sendOffOnError = true;
                    action = switchOn ? "lock" : "unlock";
                    actionStatus = api.controlLock(switchOn);
                    break;
                case CHANNEL_CONTROL_CLIMATER:
                    sendOffOnError = true;
                    action = switchOn ? "startClimater" : "stopClimater";
                    actionStatus = api.controlClimater(switchOn, getHeaterSource());
                    break;
                case CHANNEL_CLIMATER_TARGET_TEMP:
                    actionStatus = api.controlClimaterTemp(((DecimalType) command).doubleValue(), getHeaterSource());
                    break;
                case CHANNEL_CONTROL_HEATSOURCE:
                    String heaterSource = command.toString().toLowerCase();
                    logger.debug("{}: Set heater source for climatisation to {}", thingId, heaterSource);
                    cache.setValue(channelId, channelUID.getId(), new StringType(heaterSource));
                    break;
                case CHANNEL_CONTROL_CHARGER:
                    sendOffOnError = true;
                    action = switchOn ? "startCharging" : "stopCharging";
                    actionStatus = api.controlCharger(switchOn);
                    break;
                case CHANNEL_CHARGER_CURRENT:
                    sendOffOnError = true;
                    actionStatus = api.controlMaxCharge(((DecimalType) command).intValue());
                    break;
                case CHANNEL_CONTROL_WINHEAT:
                    sendOffOnError = true;
                    action = switchOn ? "startWindowHeat" : "stopWindowHeat";
                    actionStatus = api.controlWindowHeating(switchOn);
                    break;
                case CHANNEL_CONTROL_PREHEAT:
                    sendOffOnError = true;
                    action = switchOn ? "startPreHeat" : "stopPreHeat";
                    actionStatus = api.controlPreHeating(switchOn, 30);
                    break;
                case CHANNEL_CONTROL_VENT:
                    sendOffOnError = true;
                    action = switchOn ? "startVentilation" : "stopVentilation";
                    actionStatus = api.controlVentilation(switchOn, getVentDuration());
                    break;
                case CHANNEL_CONTROL_DURATION:
                    DecimalType value = new DecimalType(((DecimalType) command).intValue());
                    logger.debug("{}: Set ventilation/pre-heat duration to {}", thingId, value);
                    cache.setValue(channelUID.getId(), value);
                    break;
                case CHANNEL_CONTROL_FLASH:
                case CHANNEL_CONTROL_HONKFLASH:
                    if (command == OnOffType.ON) {
                        sendOffOnError = true;
                        State point = cache.getValue(mkChannelId(CHANNEL_GROUP_LOCATION, CHANNEL_LOCATTION_GEO));
                        if (point != UnDefType.NULL) {
                            actionStatus = api.controlHonkFlash(CHANNEL_CONTROL_HONKFLASH.equals(channelId),
                                    (PointType) point, getHfDuration());
                        } else {
                            logger.warn("{}: Geo position is not available, can't execute command", thingId);
                        }
                    }
                    break;
                case CHANNEL_CONTROL_HFDURATION:
                    DecimalType hfd = new DecimalType(((DecimalType) command).intValue());
                    logger.debug("{}: Set honk%flash duration to {}", thingId, hfd);
                    cache.setValue(channelUID.getId(), hfd);
                    break;
                default:
                    logger.info("{}: Channel {} is unknown, command {} ignored", thingId, channelId, command);
                    break;
            }
        } catch (CarNetException e) {
            CarNetApiErrorDTO res = e.getApiResult().getApiError();
            if (res.isOpAlreadyInProgress()) {
                logger.warn("{}: \"An operation is already in progress, request was rejected!\"", thingId);
            }
            if (e.isTooManyRequests()) {
                logger.warn("{}: API reported 'Too many requests', slow down updates", thingId);
            } else {
                error = getError(e);
                logger.warn("{}: {}", thingId, error.toString());
            }
        } catch (RuntimeException e) {
            error = "General Error: " + getString(e.getMessage());
            logger.warn("{}: {}", thingId, error, e);
        }

        if (!action.isEmpty()) {
            logger.debug("{}: Action {} submitted, initial status={}", thingId, action, actionStatus);
        }
        if (!error.isEmpty()) {
            if (sendOffOnError) {
                updateState(channelUID.getId(), OnOffType.OFF);
            }
        }
    }

    /**
     * This routine is called every time the Thing configuration has been changed (e.g. PaperUI)
     */
    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("{}: Thing config updated.", thingId);
        super.handleConfigurationUpdate(configurationParameters);
        initializeThing();
        forceUpdate = true;
    }

    private boolean updateVehicleStatus() throws CarNetException {
        boolean pending = false;
        // check for pending refresh
        Map<String, CarNetPendingRequest> requests = api.getPendingRequests();
        for (Map.Entry<String, CarNetPendingRequest> e : requests.entrySet()) {
            CarNetPendingRequest request = e.getValue();
            if (CNAPI_SERVICE_VEHICLE_STATUS_REPORT.equalsIgnoreCase(request.service)) {
                pending = true;
            }
        }

        if (!pending && requestStatus) {
            String status = api.refreshVehicleStatus();
            logger.debug("{}: Vehicle status refresh initiated, status={}", thingId, status);
            requestStatus = false;
            updateChannel(CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_UPDATE, OnOffType.OFF);
        }

        /*
         * Iterate all enabled services and poll for updates
         */
        boolean updated = false;
        for (Map.Entry<String, CarNetBaseService> s : services.entrySet()) {
            updated |= s.getValue().update();
        }
        return updateLastUpdate(updated);
    }

    public void checkPendingRequests() {
        Map<String, CarNetPendingRequest> requests = api.getPendingRequests();
        if (!requests.isEmpty()) {
            logger.debug("{}: Checking status for {} pending requets", thingId, requests.size());
            for (Map.Entry<String, CarNetPendingRequest> e : requests.entrySet()) {
                CarNetPendingRequest request = e.getValue();
                try {
                    request.status = api.getRequestStatus(request.requestId, "");
                } catch (CarNetException ex) {
                    CarNetApiErrorDTO error = ex.getApiResult().getApiError();
                    if (error.isTechValidationError()) {
                        // Id is no longer valid
                        request.status = CNAPI_REQUEST_ERROR;
                    }
                }
            }
        }
    }

    private String getHeaterSource() {
        State value = cache.getValue(CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_HEATSOURCE);
        return value != UnDefType.NULL ? ((StringType) value).toString().toLowerCase() : CNAPI_HEATER_SOURCE_ELECTRIC;
    }

    private int getVentDuration() {
        State state = cache.getValue(CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_DURATION);
        return state != UnDefType.NULL ? ((DecimalType) state).intValue() : VENT_DEFAULT_DURATION_MIN;
    }

    private int getHfDuration() {
        State state = cache.getValue(CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_HFDURATION);
        return state != UnDefType.NULL ? ((DecimalType) state).intValue() : HF_DEFAULT_DURATION_SEC;
    }

    @Override
    public void onActionSent(String service, String action, String requestId) {
        logger.debug("{}: Action {}.{} sent, ID={}", thingId, service, action, requestId);
        updateActionStatus(service, action, CNAPI_REQUEST_STARTED);
    }

    @Override
    public void onActionTimeout(String service, String action, String requestId) {
        logger.debug("{}: Timeout onaction {}.{} (ID {}): New status={}", thingId, service, action, requestId);
        updateActionStatus(service, action, CNAPI_REQUEST_TIMEOUT);
    }

    @Override
    public void onActionResult(String service, String action, String requestId, String status, String statusDetail) {
        logger.debug("{}: Update to action {}.{} (ID {}): New status={}", thingId, service, action, requestId,
                statusDetail);
        updateActionStatus(service, action, statusDetail);
    }

    private boolean updateActionStatus(String service, String action, String statusDetail) {
        boolean updated = false;
        updated |= updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION, getStringType(service + "." + action));
        updated |= updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION_STATUS, getStringType(statusDetail));
        updated |= updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION_PENDING,
                CarNetPendingRequest.isInProgress(statusDetail) ? OnOffType.ON : OnOffType.OFF);

        if (!CarNetPendingRequest.isInProgress(statusDetail)) {
            String channel = "";
            switch (action) {
                case CNAPI_CMD_FLASH:
                    channel = CHANNEL_CONTROL_FLASH;
                    break;
                case CNAPI_CMD_HONK_FLASH:
                    channel = CHANNEL_CONTROL_HONKFLASH;
                    break;
            }
            if (!channel.isEmpty()) {
                updateChannel(CHANNEL_GROUP_CONTROL, channel, OnOffType.OFF);
            }

            forceUpdate = true; // refresh vehicle status
            updated = true;
        }
        return updateLastUpdate(updated);
    }

    private boolean updateLastUpdate(boolean updated) {
        if (updated) {
            updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_UPDATED, getTimestamp(zoneId));
        }
        return updated;
    }

    /**
     * Sets up a polling job (using the scheduler) with the given interval.
     *
     * @param initialWaitTime The delay before the first refresh. Maybe 0 to immediately
     *            initiate a refresh.
     */
    private void setupPollingJob() {
        cancelPollingJob();
        logger.debug("Setting up polling job with an interval of {} seconds", config.vehicle.pollingInterval * 60);

        pollingJob = scheduler.scheduleWithFixedDelay(() -> {
            ++updateCounter;
            if ((updateCounter % API_REQUEST_CHECK_INT) == 0) {
                // Check results for pending requests, remove expires ones from the list
                checkPendingRequests();
            }

            if (forceUpdate || (updateCounter % skipCount == 0)) {
                CarNetAccountHandler handler = accountHandler;
                if ((handler != null) && (handler.getThing().getStatus() == ThingStatus.ONLINE)) {
                    String error = "";
                    try {
                        ThingStatus s = getThing().getStatus();
                        boolean initialized = true;
                        boolean offline = (s == ThingStatus.UNKNOWN) || (s == ThingStatus.OFFLINE);
                        if (offline) {
                            initialized = initializeThing();
                        }
                        if (initialized) {
                            updateVehicleStatus(); // on success thing must be online
                            if (getThing().getStatus() != ThingStatus.ONLINE) {
                                logger.debug("{}: Thing is now online", thingId);
                                updateStatus(ThingStatus.ONLINE);
                                updateAllChannels();
                            }
                        }
                    } catch (CarNetException e) {
                        if (e.isTooManyRequests() || e.isHttpNotModified()) {
                            logger.debug("{}: Status update failed, ignore temporary error (HTTP {})", thingId,
                                    e.getApiResult().httpCode);
                        } else {
                            error = getError(e);
                        }
                    } catch (RuntimeException e) {
                        error = "General Error: " + getString(e.getMessage());
                        logger.warn("{}: {}", thingId, error, e);
                    }

                    if (!error.isEmpty()) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error);
                    }

                    if ((updateCounter >= cacheCount) && !cache.isEnabled()) {
                        logger.debug("{}: Enabling channel cache", thingId);
                        cache.enable();
                    }
                }
                forceUpdate = false;
            }

        }, 1, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    /**
     * Cancels the polling job (if one was setup).
     */
    private void cancelPollingJob() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(false);
        }
    }

    private boolean createChannels(List<ChannelIdMapEntry> channels) {
        boolean created = false;

        ThingBuilder updatedThing = editThing();
        for (ChannelIdMapEntry channelDef : channels) {
            if (channelDef.disabled) {
                logger.debug("{}: Channel {} is disabled, skip", thingId, channelDef.symbolicName);
                continue;
            }

            String channelId = channelDef.channelName;
            String groupId = channelDef.groupName.isEmpty() ? CHANNEL_GROUP_STATUS : channelDef.groupName;
            String itemType = channelDef.itemType.isEmpty() ? ITEMT_NUMBER : channelDef.itemType;
            ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, channelId);

            // check if channelTypeUID exists in the registry, if not create it
            boolean cte = this.channelTypeProvider.channelTypeExists(channelTypeUID, null);
            if (!cte) {
                logger.debug("{}: Channel type {} doesn't exist, creating", thingId, channelTypeUID.getAsString());
                ChannelType ct = ChannelTypeBuilder.state(channelTypeUID, channelDef.getLabel(), itemType)
                        .withDescription("Auto-created for " + channelTypeUID.getId()).build();
                this.channelTypeProvider.addChannelType(ct);
            }

            if (getThing().getChannel(groupId + "#" + channelId) == null) { // only if not yet exist
                // the channel does not exist yet, so let's add it
                logger.debug("{}: Creating channel {}#{}, type {}", thingId, groupId, channelId, itemType);
                String label = getChannelAttribute(channelId, "label");
                String description = getChannelAttribute(channelId, "description");
                if (label.isEmpty() || channelDef.itemType.isEmpty()) {
                    label = channelDef.symbolicName;
                }
                Channel channel = ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), mkChannelId(groupId, channelId)), itemType)
                        .withType(channelTypeUID).withLabel(label).withDescription(description)
                        .withKind(ChannelKind.STATE).build();
                updatedThing.withChannel(channel);
                created = true;
            }
        }

        updateThing(updatedThing.build());
        return created;
    }

    public boolean addChannel(Map<String, ChannelIdMapEntry> channels, String group, String channel, String itemType,
            @Nullable Unit<?> unit, boolean advanced, boolean readOnly) {
        if (!channels.containsKey(mkChannelId(group, channel))) {
            logger.debug("{}: Adding channel definition for channel {}", thingId, mkChannelId(group, channel));
            channels.put(mkChannelId(group, channel), idMapper.add(group, channel, itemType, unit, advanced, readOnly));
            return true;
        }
        return false;
    }

    private String getChannelAttribute(String channelId, String attribute) {
        String key = "channel-type.carnet." + channelId + "." + attribute;
        String value = resources.getText(key);
        return !value.equals(key) ? value : "";
    }

    private void updateAllChannels() {
        for (Map.Entry<String, State> s : cache.getChannelData().entrySet()) {
            updateState(s.getKey(), s.getValue());
        }
    }

    public boolean updateChannel(String channelId, State value) {
        return cache.updateChannel(channelId, value, false);
    }

    public boolean updateChannel(String group, String channel, State value) {
        return updateChannel(mkChannelId(group, channel), value);
    }

    public boolean updateChannel(String group, String channel, State value, Unit<?> unit) {
        return updateChannel(group, channel, toQuantityType((Number) value, unit));
    }

    public boolean updateChannel(String group, String channel, State value, int digits, Unit<?> unit) {
        return updateChannel(group, channel, toQuantityType(((DecimalType) value).doubleValue(), digits, unit));
    }

    public boolean updateChannel(Channel channel, State value) {
        return updateChannel(channel.getUID().getId(), value);
    }

    public boolean publishState(String channelId, State value) {
        if (!stopping && isLinked(channelId)) {
            updateState(channelId, value);
            return true;
        }
        return false;
    }

    private String getError(CarNetException e) {
        CarNetApiResult res = e.getApiResult();
        if (res.httpCode == HttpStatus.FORBIDDEN_403) {
            logger.info("{}: API Service is not available: ", thingId);
            return "";
        }

        String reason = "";
        CarNetApiErrorDTO error = e.getApiResult().getApiError();
        if (!error.isError()) {
            return getString(e.getMessage());
        } else {
            logger.info("{}: API Call failed: {}", thingId, getString(e.getMessage()));
            reason = getReason(error);
            if (!reason.isEmpty()) {
                logger.debug("{}: {}", thingId, reason);
            }
        }
        if (error.isSecurityClass()) {
            String message = getApiStatus(error.description, API_STATUS_CLASS_SECURUTY);
            logger.debug("{}: {}({})", thingId, message, error.description);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
        }

        CarNetApiResult http = e.getApiResult();
        if (!http.isHttpOk() && !http.response.isEmpty()) {
            logger.debug("{}: HTTP response: {}", thingId, http.response);
        }
        String message = "";
        if (!error.code.isEmpty()) {
            String msgId = API_STATUS_MSG_PREFIX + "." + error.code;
            message = resources.get(msgId);
            if (message.equals(msgId)) {
                // No user friendly message for this code was found, so output the raw description
                message = message + " - " + error.description;
            }
        }
        logger.debug("{}: {}", thingId, message);
        return message;
    }

    private String getReason(CarNetApiErrorDTO error) {
        CNErrorMessage2Details details = error.details;
        if (details != null) {
            return getString(details.reason);
        }
        return "";
    }

    private String getApiStatus(String errorMessage, String errorClass) {
        if (errorMessage.contains(errorClass)) {
            // extract the error code like VSR.security.9007
            String key = API_STATUS_MSG_PREFIX
                    + substringBefore(substringAfterLast(errorMessage, API_STATUS_CLASS_SECURUTY + "."), ")").trim();
            return resources.get(key);
        }
        return "";
    }

    /**
     * Register all available services
     */
    private void registerServices() {
        services.clear();
        addService(new CarNetServiceStatus(this, api));
        addService(new CarNetServiceCarFinder(this, api));
        addService(new CarNetServiceRLU(this, api));
        addService(new CarNetServiceClimater(this, api));
        addService(new CarNetServicePreHeat(this, api));
        addService(new CarNetServiceCharger(this, api));
        addService(new CarNetServiceTripData(this, api));
        addService(new CarNetServiceDestinations(this, api));
        addService(new CarNetServiceHonkFlash(this, api));
        addService(new CarNetServiceGeoFenceAlerts(this, api));
        addService(new CarNetServiceSpeedAlerts(this, api));
    }

    private boolean addService(CarNetBaseService service) {
        String serviceId = service.getServiceId();
        boolean available = false;
        if (!services.containsKey(serviceId) && service.isEnabled()) {
            services.put(serviceId, service);
            available = true;
        }
        logger.debug("{}: Remote Control Service {} {} available", thingId, serviceId, available ? "is" : "is NOT");
        return available;
    }

    public CarNetCombinedConfig getThingConfig() {
        return config;
    }

    public CarNetIChanneldMapper getIdMapper() {
        return idMapper;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    @Override
    public void dispose() {
        stopping = true;
        logger.debug("Handler has been disposed");
        cancelPollingJob();
        super.dispose();
    }
}
