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

import static org.openhab.binding.carnet.internal.BindingConstants.*;
import static org.openhab.binding.carnet.internal.CarUtils.*;
import static org.openhab.binding.carnet.internal.api.carnet.CarNetApiConstants.*;

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
import org.openhab.binding.carnet.internal.ChannelCache;
import org.openhab.binding.carnet.internal.TextResources;
import org.openhab.binding.carnet.internal.api.ApiBaseService;
import org.openhab.binding.carnet.internal.api.ApiBrandInterface;
import org.openhab.binding.carnet.internal.api.ApiDataTypesDTO.VehicleDetails;
import org.openhab.binding.carnet.internal.api.ApiErrorDTO;
import org.openhab.binding.carnet.internal.api.ApiEventListener;
import org.openhab.binding.carnet.internal.api.ApiException;
import org.openhab.binding.carnet.internal.api.ApiResult;
import org.openhab.binding.carnet.internal.api.brand.BrandNull;
import org.openhab.binding.carnet.internal.api.carnet.CarNetPendingRequest;
import org.openhab.binding.carnet.internal.config.CombinedConfig;
import org.openhab.binding.carnet.internal.config.VehicleConfiguration;
import org.openhab.binding.carnet.internal.provider.CarChannelTypeProvider;
import org.openhab.binding.carnet.internal.provider.ChannelDefinitions;
import org.openhab.binding.carnet.internal.provider.ChannelDefinitions.ChannelIdMapEntry;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
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
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link VehicleBaseHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Markus Michels - Initial contribution
 * @author Lorenzo Bernardi - Additional contribution
 *
 */
@NonNullByDefault
public abstract class VehicleBaseHandler extends BaseThingHandler implements BridgeListener, ApiEventListener {
    private final Logger logger = LoggerFactory.getLogger(VehicleBaseHandler.class);
    protected final TextResources resources;
    protected final ChannelDefinitions idMapper;
    protected final CarChannelTypeProvider channelTypeProvider;
    protected final ChannelCache cache;
    protected final int cacheCount = 20;
    protected final ZoneId zoneId;

    public final String thingId;
    protected ApiBrandInterface api = new BrandNull();
    protected @Nullable AccountHandler accountHandler;
    protected @Nullable ScheduledFuture<?> pollingJob;
    protected int updateCounter = 0;
    protected int skipCount = 1;
    protected boolean forceUpdate = false; // true: update status on next polling cycle
    protected boolean requestStatus = false; // true: request update from vehicle
    protected boolean channelsCreated = false;
    protected boolean stopping = false;

    protected Map<String, ApiBaseService> services = new LinkedHashMap<>();
    protected CombinedConfig config = new CombinedConfig();

    public VehicleBaseHandler(Thing thing, TextResources resources, ZoneId zoneId, ChannelDefinitions idMapper,
            CarChannelTypeProvider channelTypeProvider) throws ApiException {
        super(thing);

        this.thingId = getThing().getUID().getId();
        this.resources = resources;
        this.idMapper = idMapper;
        this.channelTypeProvider = channelTypeProvider;
        this.zoneId = zoneId;
        this.cache = new ChannelCache(this, thingId);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing!");
        updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "Initializing");
        scheduler.schedule(() -> {
            // Register listener and wait for account being ONLINE
            AccountHandler handler = null;
            Bridge bridge = getBridge();
            if (bridge != null) {
                handler = (AccountHandler) bridge.getHandler();
            }
            if ((handler == null)) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                        "Account Thing is not initialized!");
                return;
            }
            accountHandler = handler;
            config = handler.getCombinedConfig();
            config.vehicle = getConfigAs(VehicleConfiguration.class);
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
            AccountHandler handler = accountHandler;
            if (handler == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                        "Account Handler not initialized!");
                return false;
            }

            config = handler.getCombinedConfig();
            config.vehicle = getConfigAs(VehicleConfiguration.class);
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
            } catch (ApiException e) {
                logger.warn("{}: Available services coould not be determined, continue with default profile", thingId);
            }

            logger.debug("{}: Active userId = {}, role = {} (securityLevel {}), status = {}, Pairing Code {}", thingId,
                    config.user.id, config.user.role, config.user.securityLevel, config.user.status,
                    config.vstatus.pairingInfo.pairingCode);

            skipCount = Math.max(config.vehicle.pollingInterval * 60 / POLL_INTERVAL_SEC, 2);
            cache.clear(); // clear any cached channels

            // Create services
            registerServices();

            // Create channels
            if (!channelsCreated) {
                // General channels
                Map<String, ChannelIdMapEntry> channels = new LinkedHashMap<>();
                addChannel(channels, CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_UPDATED, ITEMT_DATETIME, null, false, true);
                addChannel(channels, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_UPDATE, ITEMT_SWITCH, null, false, false);
                createBrandChannels(channels);

                // Add channels based on service information
                for (Map.Entry<String, ApiBaseService> s : services.entrySet()) {
                    ApiBaseService service = s.getValue();
                    if (!service.createChannels(channels)) {
                        logger.debug("{}: Service {} is not available, disable", thingId, service.getServiceId());
                        service.disable();
                    }
                }

                logger.debug("{}: Creating {} channels", thingId, channels.size());
                idMapper.dumpChannelDefinitions();
                createChannels(new ArrayList<>(channels.values()));
                channelsCreated = true;

                for (int i = 0; i < config.vstatus.imageUrls.length; i++) {
                    updateChannel(CHANNEL_GROUP_PICTURES, CHANNEL_PICTURES_IMG_PREFIX + (i + 1),
                            new StringType(config.vstatus.imageUrls[i]));
                }

            }
        } catch (ApiException e) {
            ApiErrorDTO res = e.getApiResult().getApiError();
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
                default:
                    if (!handleBrandCommand(channelUID, command)) {
                        logger.info("{}: Channel {} is unknown, command {} ignored", thingId, channelId, command);
                    }
                    break;
            }
        } catch (ApiException e) {
            ApiErrorDTO res = e.getApiResult().getApiError();
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
    public void informationUpdate(@Nullable List<VehicleDetails> vehicleList) {
        forceUpdate = true;
        channelsCreated = false;
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

    protected boolean updateVehicleStatus() throws ApiException {
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
            try {
                requestStatus = false;
                String status = api.refreshVehicleStatus();
                logger.debug("{}: Vehicle status refresh initiated, status={}", thingId, status);
                updateChannel(CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_UPDATE, OnOffType.OFF);
            } catch (ApiException e) {
                logger.debug("{}: Unable to request status refresh from vehicle: {}", thingId, e.toString());
            }
        }

        /*
         * Iterate all enabled services and poll for updates
         * If a single service poll fails, continue anyways
         * unless the access token expired and can't be renewed
         */
        boolean updated = false;
        for (Map.Entry<String, ApiBaseService> s : services.entrySet()) {
            ApiBaseService service = s.getValue();
            try {
                updated |= service.update();
            } catch (ApiException e) {
                logger.debug("{}: Unable to get updates from service {}: {}", thingId, service.getServiceId(),
                        e.toString());
                if (!api.isAccessTokenValid()) {
                    logger.debug("{}: Access token became invalid, cancel update", thingId);
                    break;
                }
            }
        }

        if (!api.isAccessTokenValid()) {
            throw new ApiException("Access Token expired, renewal failed!");
        }

        return updateLastUpdate(updated);
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

    public State getChannelValue(String group, String channel) {
        return cache.getValue(group, channel);
    }

    @Override
    public void onActionSent(String service, String action, String requestId) {
        logger.debug("{}: Action {}.{} sent, ID={}", thingId, service, action, requestId);
        updateActionStatus(service, action, CNAPI_REQUEST_STARTED);
    }

    @Override
    public void onActionTimeout(String service, String action, String requestId) {
        logger.debug("{}: Timeout on action {}.{} (ID {})", thingId, service, action, requestId);
        updateActionStatus(service, action, CNAPI_REQUEST_TIMEOUT);
    }

    @Override
    public void onActionResult(String service, String action, String requestId, String status, String statusDetail) {
        logger.debug("{}: Update to action {}.{} (ID {}): New status={}", thingId, service, action, requestId,
                statusDetail);
        updateActionStatus(service, action, statusDetail);
    }

    @Override
    public void onActionNotification(String service, String action, String message) {
        logger.info("{}: Status from service {}.{}: {}", thingId, service, action, message);
    }

    @Override
    public void onRateLimit(int rateLimit) {
        updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_RATELIM, new DecimalType(rateLimit));
    }

    public boolean updateActionStatus(String service, String action, String statusDetail) {
        return updateLastUpdate(true);
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
                api.checkPendingRequests();
            }

            if (forceUpdate || (updateCounter % skipCount == 0)) {
                AccountHandler handler = accountHandler;
                if (handler != null) {
                    String error = "";
                    boolean initialized = true;
                    try {
                        ThingStatus s = getThing().getStatus();
                        boolean offline = (s == ThingStatus.UNKNOWN) || (s == ThingStatus.OFFLINE);
                        if (offline) {
                            initialized = initializeThing();
                        }
                        if (initialized) {
                            updateVehicleStatus(); // on success thing must be online
                        }
                    } catch (ApiException e) {
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
                    if (error.isEmpty()) {
                        if (getThing().getStatus() != ThingStatus.ONLINE) {
                            logger.debug("{}: Thing is now online", thingId);
                            updateAllChannels();
                            updateStatus(ThingStatus.ONLINE);
                        }
                    } else {
                        if (getThing().getStatus() != ThingStatus.OFFLINE) {
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error);
                        }
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

    private void cancelPollingJob() {
        // Cancels the polling job (if one was setup).
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(false);
        }
    }

    /**
     * Dynamically create missing channels from channel definition
     *
     * @param channels List of channels to create (based on available services)
     * @return
     */
    private boolean createChannels(List<ChannelIdMapEntry> channels) {
        boolean created = false;

        ThingBuilder updatedThing = editThing();
        for (ChannelIdMapEntry channelDef : channels) {
            if (channelDef.disabled) {
                logger.debug("{}: Channel {} is disabled, skip", thingId, channelDef.symbolicName);
                continue;
            }

            String groupId = channelDef.groupName.isEmpty() ? CHANNEL_GROUP_STATUS : channelDef.groupName;
            String channelId = channelDef.channelName;
            if (getThing().getChannel(mkChannelId(groupId, channelId)) == null) { // only if not yet exist
                ChannelTypeUID channelTypeUID = channelDef.getChannelTypeUID();
                String itemType = channelDef.itemType.isEmpty() ? ITEMT_NUMBER : channelDef.itemType;

                // the channel does not exist yet, so let's add it
                logger.debug("{}: Creating channel {}#{}, type {}, UID={}", thingId, groupId, channelId, itemType,
                        channelTypeUID.toString());
                channelTypeProvider.addChannelGroupType(groupId);
                channelTypeProvider.addChannelType(channelTypeUID); // make sure ChannelType is defined
                Channel channel = ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), mkChannelId(groupId, channelId)), itemType)
                        .withType(channelTypeUID).withLabel(channelDef.getLabel()).withKind(ChannelKind.STATE).build();
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

    protected void updateAllChannels() {
        for (Map.Entry<String, State> s : cache.getChannelData().entrySet()) {
            updateState(s.getKey(), s.getValue());
        }
    }

    protected boolean updateLastUpdate(boolean updated) {
        if (updated) {
            updateChannel(CHANNEL_GROUP_GENERAL, CHANNEL_GENERAL_UPDATED, getTimestamp(zoneId));
        }
        return updated;
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

    protected String getError(ApiException e) {
        ApiResult res = e.getApiResult();
        if (res.httpCode == HttpStatus.FORBIDDEN_403) {
            logger.info("{}: API Service is not available: ", thingId);
            return "";
        }

        String reason = "";
        ApiErrorDTO error = e.getApiResult().getApiError();
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

        ApiResult http = e.getApiResult();
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

    protected String getReason(ApiErrorDTO error) {
        return "";
    }

    public void registerServices() {
        // default: none
    }

    protected boolean addService(ApiBaseService service) {
        String serviceId = service.getServiceId();
        boolean available = false;
        if (!services.containsKey(serviceId) && service.isEnabled()) {
            services.put(serviceId, service);
            available = true;
        }
        logger.debug("{}: Remote Control Service {} {} available", thingId, serviceId, available ? "is" : "is NOT");
        return available;
    }

    protected String getApiStatus(String errorMessage, String errorClass) {
        if (errorMessage.contains(errorClass)) {
            // extract the error code like VSR.security.9007
            String key = API_STATUS_MSG_PREFIX
                    + substringBefore(substringAfterLast(errorMessage, API_STATUS_CLASS_SECURUTY + "."), ")").trim();
            return resources.get(key);
        }
        return "";
    }

    public CombinedConfig getThingConfig() {
        return config;
    }

    public ChannelDefinitions getIdMapper() {
        return idMapper;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public boolean createBrandChannels(Map<String, ChannelIdMapEntry> channels) {
        return false;
    }

    /**
     * Device specific command handlers are overriding this method to do additional stuff
     */
    public boolean handleBrandCommand(ChannelUID channelUID, Command command) throws ApiException {
        return false;
    }

    @Override
    public void dispose() {
        stopping = true;
        logger.debug("Handler has been disposed");
        cancelPollingJob();
        super.dispose();
    }
}