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
package org.openhab.binding.shelly.internal.manager;

import static org.eclipse.smarthome.core.thing.Thing.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.manager.ShellyManagerConstants.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiResult;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyThingConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyBaseHandler;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * {@link ShellyManagerFwUpdatePage} implements the Shelly Manager's page template
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyManagerPage {
    private final Logger logger = LoggerFactory.getLogger(ShellyManagerPage.class);
    protected final Map<String, ShellyBaseHandler> thingHandlers;
    protected final HttpClient httpClient;
    protected final ConfigurationAdmin configurationAdmin;
    protected final ShellyBindingConfiguration bindingConfig = new ShellyBindingConfiguration();
    protected final String localIp;
    protected final int localPort;

    protected final Map<String, String> htmlTemplates = new HashMap<>();
    protected final Gson gson = new Gson();

    protected final ShellyManagerCache<String, FwRepoEntry> firmwareRepo = new ShellyManagerCache<>(15 * 60 * 1000);
    protected final ShellyManagerCache<String, FwArchList> firmwareArch = new ShellyManagerCache<>(15 * 60 * 1000);

    public static class ShellyMgrResponse {
        public @Nullable Object data = "";
        public String mimeType = "";
        public String redirectUrl = "";
        public int code;
        public Map<String, String> headers = new HashMap<>();

        public ShellyMgrResponse() {
            init("", HttpStatus.OK_200, "text/html", null);
        }

        public ShellyMgrResponse(Object data, int code) {
            init(data, code, "text/html", null);
        }

        public ShellyMgrResponse(Object data, int code, String mimeType) {
            init(data, code, mimeType, null);
        }

        public ShellyMgrResponse(Object data, int code, String mimeType, Map<String, String> headers) {
            init(data, code, mimeType, headers);
        }

        private void init(Object message, int code, String mimeType, @Nullable Map<String, String> headers) {
            this.data = message;
            this.code = code;
            this.mimeType = mimeType;
            this.headers = headers != null ? headers : new TreeMap<>();
        }

        public void setRedirect(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }
    }

    public static class FwArchEntry {
        // {"version":"v1.5.10","file":"SHSW-1.zip"}
        public @Nullable String version;
        public @Nullable String file;
    }

    public static class FwArchList {
        public @Nullable ArrayList<FwArchEntry> versions;
    }

    public static class FwRepoEntry {
        public @Nullable String url; // prod
        public @Nullable String version;

        public @Nullable String beta_url; // beta version if avilable
        public @Nullable String beta_ver;
    }

    public ShellyManagerPage(ConfigurationAdmin configurationAdmin, HttpClient httpClient, String localIp,
            int localPort, Map<String, ShellyBaseHandler> thingHandlers) {
        this.configurationAdmin = configurationAdmin;
        this.thingHandlers = thingHandlers;
        this.httpClient = httpClient;
        this.localIp = localIp;
        this.localPort = localPort;
    }

    public ShellyMgrResponse generateContent(String path, Map<String, String[]> parameters) throws ShellyApiException {
        return new ShellyMgrResponse("Invalid Request", HttpStatus.BAD_REQUEST_400);
    }

    protected String getUrlParm(Map<String, String[]> parameters, String param) {
        String[] p = parameters.get(param);
        String value = "";
        if (p != null) {
            value = getString(p[0]);
        }
        return value;
    }

    protected String loadHTML(String template) throws ShellyApiException {
        if (htmlTemplates.containsKey(template)) {
            return getString(htmlTemplates.get(template));
        }

        String html = "";
        String file = TEMPLATE_PATH + template;
        logger.debug("Read HTML from {}", file);
        ClassLoader cl = ShellyBaseHandler.class.getClassLoader();
        if (cl != null) {
            try (InputStream inputStream = cl.getResourceAsStream(file)) {
                if (inputStream != null) {
                    // String html = IOUtils.toString(inputStream, UTF_8);
                    html = IOUtils.toString(inputStream);
                    htmlTemplates.put(template, html);
                }
            } catch (IOException e) {
                throw new ShellyApiException("Unable to read " + file + " from bundle resources!", e);
            }
        }
        return html;
    }

    protected String loadHTML(String template, Map<String, String> properties) throws ShellyApiException {
        properties.put("uri", SHELLY_MANAGER_URI);
        String html = loadHTML(template);
        return fillAttributes(html, properties);
    }

    protected Map<String, String> fillProperties(Map<String, String> properties, String uid, ShellyBaseHandler th) {
        try {
            Configuration serviceConfig = configurationAdmin.getConfiguration("binding." + BINDING_ID);
            bindingConfig.updateFromProperties(serviceConfig.getProperties());
        } catch (IOException e) {
            logger.debug("ShellyManager: Unable to get bindingConfig");
        }

        properties.putAll(th.getThing().getProperties());
        properties.putAll(th.getStatsProp());

        Thing thing = th.getThing();
        properties.put("thingName", getString(thing.getLabel()));
        properties.put("thingStatus", getString(thing.getStatus().toString()));
        ThingStatusDetail detail = thing.getStatusInfo().getStatusDetail();
        properties.put("thingStatusDetail", detail.equals(ThingStatusDetail.NONE) ? "" : getString(detail.toString()));
        properties.put("thingStatusDescr", getString(thing.getStatusInfo().getDescription()));
        properties.put("uid", uid);
        ShellyDeviceProfile profile = th.getProfile();
        ShellyThingConfiguration config = thing.getConfiguration().as(ShellyThingConfiguration.class);
        for (Map.Entry<String, Object> p : thing.getConfiguration().getProperties().entrySet()) {
            String key = p.getKey();
            if (p.getValue() != null) {
                String value = p.getValue().toString();
                properties.put(key, value);
            }
        }

        State state = th.getChannelValue(CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_NAME);
        if (state != UnDefType.NULL) {
            addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_NAME);
        } else {
            // If the Shelly doesn't provide a device name (not configured) we use the service name
            String deviceName = getDeviceName(properties);
            properties.put(PROPERTY_DEV_NAME,
                    !deviceName.isEmpty() ? deviceName : getString(properties.get(PROPERTY_SERVICE_NAME)));
        }

        if (config.userId.isEmpty()) {
            // Get defauls from Binding Config
            properties.put("userId", bindingConfig.defaultUserId);
            properties.put("password", bindingConfig.defaultPassword);
        }

        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_RSSI);
        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_UPTIME);
        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_HEARTBEAT);
        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ITEMP);
        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_WAKEUP);
        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_CHARGER);
        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_UPDATE);
        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ALARM);
        addAttribute(properties, th, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_CHARGER);

        // Shelly H&T: When external power is connected the battery level is not valid
        if (!profile.isHT || (getInteger(profile.settings.externalPower) == 0)) {
            addAttribute(properties, th, CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LEVEL);
        } else {
            properties.put(CHANNEL_SENSOR_BAT_LEVEL, "USB");
        }

        String wiFiSignal = getString(properties.get(CHANNEL_DEVST_RSSI));
        properties.put("imgWiFi", !wiFiSignal.isEmpty() ? "imgWiFi" + wiFiSignal : "");
        String statusIcon = "";
        ThingStatus ts = th.getThing().getStatus();
        switch (ts) {
            case UNINITIALIZED:
            case REMOVED:
            case REMOVING:
                statusIcon = ICON_UNINITIALIZED;
                break;
            case OFFLINE:
                ThingStatusDetail sd = th.getThing().getStatusInfo().getStatusDetail();
                if (uid.contains(THING_TYPE_SHELLYUNKNOWN_STR) || (sd == ThingStatusDetail.CONFIGURATION_ERROR)
                        || (sd == ThingStatusDetail.HANDLER_CONFIGURATION_PENDING)) {
                    statusIcon = ICON_CONFIG;
                    break;
                }
            default:
                statusIcon = ts.toString();
        }
        properties.put(ATTRIBUTE_STATUS_ICON, statusIcon.toLowerCase());

        return properties;
    }

    private void addAttribute(Map<String, String> properties, ShellyBaseHandler thingHandler, String group,
            String attribute) {
        State state = thingHandler.getChannelValue(group, attribute);
        String value = "";
        if (state != UnDefType.NULL) {
            if (state instanceof DateTimeType) {
                DateTimeType dt = (DateTimeType) state;
                value = dt.format(null).replace('T', ' ').replace('-', '/');
            } else {
                value = state.toString();
            }
        }
        properties.put(attribute, value);
    }

    protected String fillAttributes(String template, Map<String, String> properties) {
        if (!template.contains("${")) {
            // no replacement necessary
            return template;
        }

        String result = template;
        for (Map.Entry<String, String> var : properties.entrySet()) {
            result = result.replaceAll(java.util.regex.Pattern.quote("${" + var.getKey() + "}"),
                    getValue(properties, var.getKey()));
        }

        if (result.contains("${")) {
            return result.replaceAll("\\Q${\\E.*}", "");
        } else {
            return result;
        }
    }

    protected String getValue(Map<String, String> properties, String attribute) {
        String value = getString(properties.get(attribute));
        if (!value.isEmpty()) {
            switch (attribute) {
                case PROPERTY_FIRMWARE_VERSION:
                    value = substringBeforeLast(value, "-");
                    break;
                case PROPERTY_UPDATE_AVAILABLE:
                    value = value.replace(OnOffType.ON.toString(), "yes");
                    value = value.replace(OnOffType.OFF.toString(), "no");
                    break;
                case CHANNEL_DEVST_HEARTBEAT:
                    break;
            }
        }
        return value;
    }

    protected FwRepoEntry getFirmwareRepoEntry(String deviceType) throws ShellyApiException {
        logger.debug("ShellyManager: Load firmware list from {}", FWREPO_PROD_URL);
        FwRepoEntry fw = null;
        if (firmwareRepo.containsKey(deviceType)) {
            fw = firmwareRepo.get(deviceType);
        }
        String json = httpGet(FWREPO_PROD_URL); // returns a strange JSON format so we are parsing this manually
        String entry = substringBetween(json, "\"" + deviceType + "\":{", "}");
        if (!entry.isEmpty()) {
            entry = "{" + entry + "}";
            /*
             * Example:
             * "SHPLG-1":{
             * "url":"http:\/\/repo.shelly.cloud\/firmware\/SHPLG-1.zip",
             * "version":"20201228-092318\/v1.9.3@ad2bb4e3",
             * "beta_url":"http:\/\/repo.shelly.cloud\/firmware\/rc\/SHPLG-1.zip",
             * "beta_ver":"20201223-093703\/v1.9.3-rc5@3f583801"
             * },
             */
            fw = fromJson(gson, entry, FwRepoEntry.class);
            firmwareRepo.put(deviceType, fw);
        }

        return fw != null ? fw : new FwRepoEntry();
    }

    protected FwArchList getFirmwareArchiveList(String deviceType) throws ShellyApiException {
        FwArchList list;
        String json = "";

        if (firmwareArch.contains(deviceType)) {
            list = firmwareArch.get(deviceType); // return from cache
            if (list != null) {
                return list;
            }
        }

        try {
            if (!deviceType.isEmpty()) {
                json = httpGet(FWREPO_ARCH_URL + "?type=" + deviceType);
            }
        } catch (ShellyApiException e) {
            logger.debug("{}: Unable to get firmware list for device type {}: {}", LOG_PREFIX, deviceType,
                    e.toString());
        }
        if (json.isEmpty() || json.startsWith("[]")) {
            // no files available for this device type
            logger.info("{}: No firmware files found for device type {}", LOG_PREFIX, deviceType);
            list = new FwArchList();
            list.versions = new ArrayList<FwArchEntry>();
        } else {
            // Create selection list
            json = "{" + json.replace("[{", "\"versions\":[{") + "}"; // make it an named array
            list = fromJson(gson, json, FwArchList.class);
        }

        // save list to cache
        firmwareArch.put(deviceType, list);
        return list;
    }

    protected String httpGet(String url) throws ShellyApiException {
        ShellyApiResult apiResult = new ShellyApiResult();

        try {
            Request request = httpClient.newRequest(url).method(HttpMethod.GET).timeout(SHELLY_API_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            request.header(HttpHeader.ACCEPT, ShellyHttpApi.CONTENT_TYPE_JSON);
            logger.trace("{}: HTTP GET {}", LOG_PREFIX, url);
            ContentResponse contentResponse = request.send();
            apiResult = new ShellyApiResult(contentResponse);
            String response = contentResponse.getContentAsString().replace("\t", "").replace("\r\n", "").trim();
            logger.trace("{}: HTTP Response {}: {}", LOG_PREFIX, contentResponse.getStatus(), response);

            // validate response, API errors are reported as Json
            if (contentResponse.getStatus() != HttpStatus.OK_200) {
                throw new ShellyApiException(apiResult);
            }
            return response;
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            throw new ShellyApiException("HTTP GET failed", e);
        }
    }

    protected static String getDeviceType(Map<String, String> properties) {
        return getString(properties.get(PROPERTY_MODEL_ID));
    }

    protected static String getDeviceIp(Map<String, String> properties) {
        return getString(properties.get("deviceIp"));
    }

    protected static String getDeviceName(Map<String, String> properties) {
        return getString(properties.get(PROPERTY_DEV_NAME));
    }

    protected static String getDisplayName(Map<String, String> properties) {
        String name = getString(properties.get(PROPERTY_DEV_NAME));
        if (name.isEmpty()) {
            name = getString(properties.get(PROPERTY_SERVICE_NAME));
        }
        return name;
    }

    protected ShellyThingConfiguration getThingConfig(ShellyBaseHandler th, Map<String, String> properties) {
        Thing thing = th.getThing();
        ShellyThingConfiguration config = thing.getConfiguration().as(ShellyThingConfiguration.class);
        if (config.userId.isEmpty()) {
            config.userId = getString(properties.get("userId"));
            config.password = getString(properties.get("password"));
        }
        return config;
    }

    protected void scheduleUpdate(ShellyBaseHandler th, String name, int delay) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                th.requestUpdates(1, true);
            }
        };
        Timer timer = new Timer(name);
        timer.schedule(task, delay * 1000);
    }
}
