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
package org.openhab.binding.carnet.internal.handler;

import static org.openhab.binding.carnet.internal.CarNetBindingConstants.*;
import static org.openhab.binding.carnet.internal.CarNetUtils.*;
import static org.openhab.binding.carnet.internal.api.CarNetApiConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelKind;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.CarNetTextResources;
import org.openhab.binding.carnet.internal.api.CarNetApi;
import org.openhab.binding.carnet.internal.api.CarNetApi.CarNetPendingRequest;
import org.openhab.binding.carnet.internal.api.CarNetApiErrorDTO;
import org.openhab.binding.carnet.internal.api.CarNetApiErrorDTO.CNErrorMessage2Details;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNOperationList.CarNetOperationList;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNPairingInfo.CarNetPairingInfo;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNVehicleData.CarNetVehicleData;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetServiceAvailability;
import org.openhab.binding.carnet.internal.api.CarNetApiResult;
import org.openhab.binding.carnet.internal.api.CarNetTokenManager;
import org.openhab.binding.carnet.internal.config.CarNetCombinedConfig;
import org.openhab.binding.carnet.internal.config.CarNetVehicleConfiguration;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper.ChannelIdMapEntry;
import org.openhab.carnet.internal.services.CarNetVehicleBaseService;
import org.openhab.carnet.internal.services.CarNetVehicleServiceCarFinder;
import org.openhab.carnet.internal.services.CarNetVehicleServiceCharger;
import org.openhab.carnet.internal.services.CarNetVehicleServiceClimater;
import org.openhab.carnet.internal.services.CarNetVehicleServiceDestinations;
import org.openhab.carnet.internal.services.CarNetVehicleServiceRLU;
import org.openhab.carnet.internal.services.CarNetVehicleServiceStatus;
import org.openhab.carnet.internal.services.CarNetVehicleServiceTripData;
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
public class CarNetVehicleHandler extends BaseThingHandler implements CarNetDeviceListener {
    private final Logger logger = LoggerFactory.getLogger(CarNetVehicleHandler.class);
    private final CarNetTextResources resources;
    private final CarNetIChanneldMapper idMapper;
    private final Map<String, Object> channelData = new HashMap<>();
    private final CarNetTokenManager tokenManager;

    public String thingId = "";
    private CarNetApi api = new CarNetApi();
    private @Nullable CarNetAccountHandler accountHandler;
    private @Nullable ScheduledFuture<?> pollingJob;
    private int updateCounter = 0;
    private int skipCount = 1;
    private boolean forceUpdate;
    private boolean channelsCreated = false;
    private boolean testData = false;

    private Map<String, CarNetVehicleBaseService> services = new LinkedHashMap<>();
    private CarNetServiceAvailability serviceAvailability = new CarNetServiceAvailability();
    private CarNetCombinedConfig config = new CarNetCombinedConfig();

    public CarNetVehicleHandler(Thing thing, CarNetTextResources resources, CarNetIChanneldMapper idMapper,
            CarNetTokenManager tokenManager) {
        super(thing);

        this.resources = resources;
        this.idMapper = idMapper;
        this.tokenManager = tokenManager;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing!");
        updateStatus(ThingStatus.UNKNOWN);

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
        thingId = getThing().getUID().getId();

        api = new CarNetApi(handler.getHttpClient(), tokenManager);

        scheduler.schedule(() -> {
            if (accountHandler != null) {
                accountHandler.registerListener(this);
                setupPollingJob();
            }
        }, 1, TimeUnit.SECONDS);
    }

    /**
     * (re-)initialize the thing
     *
     * @return true=successful
     */
    boolean initializeThing() {
        channelData.clear(); // clear any cached channels
        boolean successful = true;
        String error = "";
        try {
            if (accountHandler != null) {
                config = accountHandler.getCombinedConfig();
            }
            config.vehicle = getConfigAs(CarNetVehicleConfiguration.class);
            Map<String, String> properties = getThing().getProperties();
            skipCount = Math.max(config.vehicle.refreshInterval / POLL_INTERVAL_SEC, 2);
            channelsCreated = false;

            String vin = "";
            if (properties.containsKey(PROPERTY_VIN)) {
                vin = properties.get(PROPERTY_VIN);
            }
            if (vin.isEmpty()) {
                logger.info("VIN not set (Thing properties)");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "VIN not set (Thing properties)");
                return false;
            }
            config.vehicle.vin = vin.toUpperCase();
            api.setConfig(config); // required to pass VIN to CarNetApi
            config.vehicle.homeRegionUrl = api.getHomeReguionUrl();
            api.setConfig(config);

            serviceAvailability = new CarNetServiceAvailability(); // init all to true
            CarNetOperationList ol = api.getOperationList();
            config.user.id = ol.userId;
            config.user.role = ol.role;
            config.user.status = ol.status;
            config.user.securityLevel = ol.securityLevel;

            CarNetVehicleData vmi = api.getVehicleManagementInfo();
            if (!vmi.isConnect) {
                logger.warn("{}: Car Connect might not be enabled!", thingId);
            }

            serviceAvailability = api.getServiceAvailability(ol);
            CarNetServiceAvailability sa = serviceAvailability;
            logger.debug(
                    "{}: Service availability: statusData: {}, tripData: {}, destinations: {}, carFinder: {}, climater: {}, charger: {}, remoteLock: {}",
                    thingId, sa.statusData, sa.tripData, sa.destinations, sa.carFinder, sa.clima, sa.charger, sa.rlu);

            CarNetPairingInfo pi = api.getPairingStatus();
            config.user.pairingCode = pi.pairingCode;
            if (!pi.isPairingCompleted()) {
                logger.warn("{}: Pairing for {} is not completed, use MMI to pair with code {}", thingId, ol.role,
                        pi.pairingCode);
            }
            logger.debug("{}: Active userId = {}, role = {} (securityLevel {}), status = {}, Pairing Code {}", thingId,
                    config.user.id, ol.role, ol.securityLevel, ol.status, config.user.pairingCode);
            api.setConfig(config);

            if (logger.isDebugEnabled() && testData) {
                // Get available services
                String h = null, ts = null, df = null, poi = null, hr = null;
                try {
                    h = api.getHistory();
                } catch (Exception e) {
                }
                try {
                    ts = api.getTripStats("shortTerm");
                } catch (Exception e) {
                }
                try {
                    df = api.getMyDestinationsFeed(config.user.id);
                } catch (Exception e) {
                }
                try {
                    poi = api.getPois();
                } catch (Exception e) {
                }
                try {
                    hr = api.getVehicleHealthReport();
                } catch (Exception e) {
                }

                logger.debug(
                        "{}: Additional Data\nHistory:{}\nTrip stats short: {}\nMyDestinationsFeed: {}\nPOIs: {}\nHealth Report: {}\n",
                        thingId, h, ts, df, poi, hr);
                testData = false;
            }

            // Create services
            services.clear();
            addService(serviceAvailability.statusData, CNAPI_SERVICE_VEHICLE_STATUS_REPORT,
                    new CarNetVehicleServiceStatus(this, api));
            addService(serviceAvailability.carFinder, CNAPI_SERVICE_CAR_FINDER,
                    new CarNetVehicleServiceCarFinder(this, api));
            addService(serviceAvailability.rlu, CNAPI_SERVICE_REMOTE_LOCK_UNLOCK,
                    new CarNetVehicleServiceRLU(this, api));
            addService(serviceAvailability.charger, CNAPI_SERVICE_REMOTE_BATTERY_CHARGE,
                    new CarNetVehicleServiceCharger(this, api));
            addService(serviceAvailability.clima, CNAPI_SERVICE_REMOTE_PRETRIP_CLIMATISATION,
                    new CarNetVehicleServiceClimater(this, api));
            addService(serviceAvailability.tripData, CNAPI_SERVICE_REMOTE_TRIP_STATISTICS,
                    new CarNetVehicleServiceTripData(this, api));
            addService(serviceAvailability.destinations, CNAPI_SERVICE_MY_AUDI_DESTINATIONS,
                    new CarNetVehicleServiceDestinations(this, api));

            if (!channelsCreated) {
                // Add additional channels
                Map<String, ChannelIdMapEntry> channels = new LinkedHashMap<>();
                for (Map.Entry<String, CarNetVehicleBaseService> s : services.entrySet()) {
                    s.getValue().createChannels(channels);
                }

                logger.debug("{}: Creating {} channels", thingId, channels.size());
                ArrayList<ChannelIdMapEntry> channelList = new ArrayList<>(channels.values());
                /*
                 * try (FileWriter myWriter = new FileWriter("carnetChannels.MD")) {
                 * String lastGroup = "";
                 * for (ChannelIdMapEntry e : channelList) {
                 * String group = lastGroup.equals(e.groupName) ? "" : e.groupName;
                 * String s = String.format("| %-12.12s | %-23.23s | %-20.20s | %-7s | %-85s |\n", group,
                 * e.channelName, e.itemType, e.readOnly ? "yes" : "no", e.getDescription());
                 * myWriter.write(s);
                 * lastGroup = e.groupName;
                 * }
                 * } catch (IOException e) {
                 * }
                 */
                createChannels(channelList);
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
        } else {
            // updateStatus(ThingStatus.ONLINE);
        }
        return successful;
    }

    /**
     * Brigde status changed
     */
    @Override
    public void stateChanged(ThingStatus status, ThingStatusDetail detail, String message) {
        forceUpdate = true;
    }

    @Override
    public void informationUpdate(@Nullable List<CarNetVehicleInformation> vehicleList) {
        forceUpdate = true;
        channelsCreated = false;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }

        ThingStatus s = getThing().getStatus();
        if ((s == ThingStatus.INITIALIZING) || (s == ThingStatus.UNKNOWN)) {
            logger.info("{}: Thing not yet fully initialized, command ignored", thingId);
            return;
        }

        String channelId = channelUID.getIdWithoutGroup();
        String error = "";
        boolean sendOffOnError = false;
        String action = "";
        String actionStatus = "";
        boolean switchOn = (command instanceof OnOffType) && (OnOffType) command == OnOffType.ON;
        try {
            switch (channelId) {
                case CHANNEL_CONTROL_UPDATE:
                    forceUpdate = true;
                    updateState(channelUID.getId(), OnOffType.OFF);
                    break;
                case CHANNEL_CONTROL_LOCK:
                    sendOffOnError = true;
                    action = switchOn ? "lock" : "unlock";
                    actionStatus = api.controlLock(switchOn);
                    break;
                case CHANNEL_CONTROL_CLIMATER:
                    sendOffOnError = true;
                    action = switchOn ? "startClimater" : "stopClimater";
                    actionStatus = api.controlClimater(switchOn);
                    break;
                case CHANNEL_CONTROL_CHARGER:
                    sendOffOnError = true;
                    action = switchOn ? "startCharging" : "stopCharging";
                    actionStatus = api.controlCharger(switchOn);
                    break;
                case CHANNEL_CONTROL_WINHEAT:
                    sendOffOnError = true;
                    action = switchOn ? "startWindowHeat" : "stopWindowHeat";
                    actionStatus = api.controlWindowHeating(switchOn);
                    break;
                case CHANNEL_CONTROL_PREHEAT:
                    sendOffOnError = true;
                    action = switchOn ? "startPreHeat" : "stopPreHeat";
                    actionStatus = api.controlPreHeating(switchOn);
                    break;
                case CHANNEL_CONTROL_VENT:
                    sendOffOnError = true;
                    action = switchOn ? "startVentilation" : "stopVentilation";
                    actionStatus = api.controlVentilation(switchOn, 15);
                    break;
                default:
                    logger.info("{}: Channel {} is unknown, command {} ignored", thingId, channelId, command);
                    break;
            }

            updateActionStatus(action, actionStatus);
            forceUpdate = true; // update on successful command
        } catch (CarNetException e) {
            CarNetApiErrorDTO res = e.getApiResult().getApiError();
            if (res.isOpAlreadyInProgress()) {
                logger.warn("{}: \"An operation is already in progress, request was rejected!\"", thingId);
            } else {
                error = getError(e);
                logger.warn("{}: {}", thingId, error.toString());
            }
        } catch (RuntimeException e) {
            error = "General Error: " + getString(e.getMessage());
            logger.warn("{}: {}", thingId, error, e);
        }

        if (!error.isEmpty()) {
            if (sendOffOnError) {
                updateState(channelUID.getId(), OnOffType.OFF);
            }
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error);
        }
    }

    private void updateActionStatus(String action, String actionStatus) {
        if (!actionStatus.isEmpty()) {
            if (!action.isEmpty()) {
                updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION, getStringType(action));
            }
            updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION_STATUS, getStringType(actionStatus));
            boolean inProgress = actionStatus.equalsIgnoreCase(CNAPI_REQUEST_IN_PROGRESS);
            updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_ACTION_PENDING,
                    inProgress ? OnOffType.ON : OnOffType.OFF);
            updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_UPDATED, getTimestamp());
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
    }

    private boolean updateVehicleStatus() throws CarNetException {
        boolean updated = false;
        for (Map.Entry<String, CarNetVehicleBaseService> s : services.entrySet()) {
            updated |= s.getValue().update();
        }

        if (updated) {
            updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_UPDATED, getTimestamp());
        }
        return updated;
    }

    public void checkPendingRequests() {
        for (Map.Entry<String, CarNetPendingRequest> e : api.getPendingRequests().entrySet()) {
            CarNetPendingRequest request = e.getValue();
            String status = "";
            try {
                status = api.getRequestStatus(request.requestId, "");
            } catch (CarNetException ex) {
                CarNetApiErrorDTO error = ex.getApiResult().getApiError();
                if (error.isTechValidationError()) {
                    // Id is no longer valid
                    status = CNAPI_REQUEST_ERROR;
                }
            }
            updateActionStatus("", status);
        }
    }

    /**
     * Sets up a polling job (using the scheduler) with the given interval.
     *
     * @param initialWaitTime The delay before the first refresh. Maybe 0 to immediately
     *            initiate a refresh.
     */
    private void setupPollingJob() {
        cancelPollingJob();
        logger.debug("Setting up polling job with an interval of {} seconds", config.vehicle.refreshInterval);

        pollingJob = scheduler.scheduleWithFixedDelay(() -> {
            ++updateCounter;

            if (updateCounter % API_REQUEST_CHECK_INT == 0) {
                // Check results for pending requests, remove expires ones from the list
                checkPendingRequests();
            }

            if (forceUpdate || (updateCounter % skipCount == 0)) {
                if ((accountHandler != null) && (accountHandler.getThing().getStatus() == ThingStatus.ONLINE)) {
                    String error = "";
                    try {
                        forceUpdate = false;
                        ThingStatus s = getThing().getStatus();
                        if ((s == ThingStatus.UNKNOWN) || (s == ThingStatus.OFFLINE)) {
                            initializeThing();
                        }
                        updateVehicleStatus();

                        updateStatus(ThingStatus.ONLINE); // on success thing must be online
                    } catch (CarNetException e) {
                        error = getError(e);
                    } catch (RuntimeException e) {
                        error = "General Error: " + getString(e.getMessage());
                        logger.warn("{}: {}", thingId, error, e);
                    }

                    if (!error.isEmpty()) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error);
                    }
                }
            }

        }, 1, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private boolean createChannels(List<ChannelIdMapEntry> channels) {
        boolean created = false;

        ThingBuilder updatedThing = editThing();
        for (ChannelIdMapEntry channelDef : channels) {
            String channelId = channelDef.channelName;
            String groupId = channelDef.groupName;
            if (groupId.isEmpty()) {
                groupId = CHANNEL_GROUP_STATUS; // default group
            }
            ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, channelId);
            if (getThing().getChannel(groupId + "#" + channelId) == null) {
                // the channel does not exist yet, so let's add it
                String itemType = channelDef.itemType.isEmpty() ? ITEMT_NUMBER : channelDef.itemType;
                logger.debug("{}: Auto-creating channel {}#{}, type {}", thingId, groupId, channelId, itemType);
                String label = getChannelAttribute(channelId, "label");
                String description = getChannelAttribute(channelId, "description");
                if (label.isEmpty() || channelDef.itemType.isEmpty()) {
                    label = channelDef.symbolicName;
                }
                Channel channel = ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), groupId + "#" + channelId), itemType)
                        .withType(channelTypeUID).withLabel(label).withDescription(description)
                        .withKind(ChannelKind.STATE).build();
                updatedThing.withChannel(channel);
                created = true;
            }
        }

        updateThing(updatedThing.build());
        return created;
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

    private String getChannelAttribute(String channelId, String attribute) {
        String key = "channel-type.carnet." + channelId + "." + attribute;
        String value = resources.getText(key);
        return !value.equals(key) ? value : "";
    }

    private boolean addService(boolean add, String serviceId, CarNetVehicleBaseService service) {
        if (add && !services.containsKey(serviceId)) {
            services.put(serviceId, service);
            return true;
        }
        return false;
    }

    public boolean updateChannel(String group, String channel, State value) {
        updateState(mkChannelId(group, channel), value);
        return true;
    }

    public boolean updateChannel(String group, String channel, State value, Unit<?> unit) {
        updateState(mkChannelId(group, channel), toQuantityType((Number) value, unit));
        return true;
    }

    public boolean updateChannel(String group, String channel, State value, int digits, Unit<?> unit) {
        updateState(mkChannelId(group, channel), toQuantityType(((DecimalType) value).doubleValue(), digits, unit));
        return true;
    }

    public boolean updateChannel(Channel channel, State value) {
        updateState(channel.getUID(), value);
        return true;
    }

    /**
     * Cancels the polling job (if one was setup).
     */
    private void cancelPollingJob() {
        if (pollingJob != null) {
            pollingJob.cancel(false);
        }
    }

    @Override
    public void dispose() {
        cancelPollingJob();
        super.dispose();
    }

    public CarNetCombinedConfig getThingConfig() {
        return config;
    }

    public CarNetIChanneldMapper getIdMapper() {
        return idMapper;
    }
}
