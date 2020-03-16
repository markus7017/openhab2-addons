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
package org.openhab.binding.shelly.internal.api;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.SHELLY_API_TIMEOUT_MS;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.urlEncode;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyControlRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySendKeyList;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySenseKeyCode;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsDevice;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsLight;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyShortLightStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyStatusLight;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyStatusRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyStatusSensor;
import org.openhab.binding.shelly.internal.config.ShellyThingConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * {@link ShellyHttpApi} wraps the Shelly REST API and provides various low level function to access the device api (not
 * cloud api).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyHttpApi {
    public static final String HTTP_HEADER_AUTH = "Authorization";
    public static final String HTTP_AUTH_TYPE_BASIC = "Basic";
    public static final String CONTENT_TYPE_XML = "text/xml; charset=UTF-8";
    public static final String CONTENT_TYPE_JSON = "application/json";

    private final Logger logger = LoggerFactory.getLogger(ShellyHttpApi.class);
    private @Nullable HttpClient httpClient;
    private final ShellyThingConfiguration config;
    private final String thingName;
    private final Gson gson = new Gson();
    private int timeoutErrors = 0;
    private int timeoutsRecovered = 0;

    private ShellyDeviceProfile profile = new ShellyDeviceProfile();

    public ShellyHttpApi() {
        thingName = "";
        config = new ShellyThingConfiguration();
    }

    public ShellyHttpApi(String thingName, ShellyThingConfiguration config, @Nullable HttpClient httpClient) {
        Validate.notNull(config, thingName + ": Shelly API: Config must not be null!");
        this.config = config;
        this.thingName = thingName;
        this.httpClient = httpClient;
    }

    public ShellySettingsDevice getDevInfo() throws ShellyApiException {
        return callApi(SHELLY_URL_DEVINFO, ShellySettingsDevice.class);
    }

    /**
     * Initialize the device profile
     *
     * @param thingType Type of DEVICE as returned from the thing properties (based on discovery)
     * @return Initialized ShellyDeviceProfile
     * @throws ShellyApiException
     */
    public ShellyDeviceProfile getDeviceProfile(String thingType) throws ShellyApiException {
        String json = request(SHELLY_URL_SETTINGS);
        if (json.contains("\"type\":\"SHDM-1\"")) {
            logger.trace("{}: Detected a Shelly Dimmer: fix Json (replace lights[] tag with dimmers[]", thingName);
            json = fixDimmerJson(json);
        }

        // Map settings to device profile for Light and Sense
        logger.debug("{}: Re-initializing device profile", thingName);
        profile.initialize(thingType, json);

        // 2nd level initialization
        profile.thingName = profile.hostname;
        if (profile.isLight && (profile.numMeters == 0)) {
            logger.debug("{}: Get number of meters from light status", thingName);
            ShellyStatusLight status = getLightStatus();
            profile.numMeters = status.meters != null ? status.meters.size() : 0;
        }
        if (profile.isSense) {
            profile.irCodes = getIRCodeList();
            logger.debug("{}: Sense stored key list loaded, {} entries.", thingName, profile.irCodes.size());
        }

        return profile;
    }

    public boolean isInitialized() {
        return profile.initialized;
    }

    /**
     * Get generic device settings/status. Json returned from API will be mapped to a Gson object
     *
     * @return Device settings/status as ShellySettingsStatus object
     * @throws ShellyApiException
     */
    public ShellySettingsStatus getStatus() throws ShellyApiException {
        String json = request(SHELLY_URL_STATUS);
        ShellySettingsStatus status = gson.fromJson(json, ShellySettingsStatus.class);
        Validate.notNull(status);
        status.json = json;
        return status;
    }

    public ShellyStatusRelay getRelayStatus(Integer relayIndex) throws ShellyApiException {
        return callApi(SHELLY_URL_STATUS_RELEAY + "/" + relayIndex.toString(), ShellyStatusRelay.class);
    }

    public void setRelayTurn(Integer id, String turnMode) throws ShellyApiException {
        Validate.notNull(profile);
        request(getControlUrlPrefix(id) + "?" + SHELLY_LIGHT_TURN + "=" + turnMode.toLowerCase());
    }

    public void setBrightness(Integer id, Integer brightness, boolean autoOn) throws ShellyApiException {
        String turn = autoOn ? SHELLY_LIGHT_TURN + "=" + SHELLY_API_ON + "&" : "";
        request(getControlUrlPrefix(id) + "?" + turn + "brightness=" + brightness.toString());
    }

    public ShellyControlRoller getRollerStatus(Integer rollerIndex) throws ShellyApiException {
        String uri = SHELLY_URL_CONTROL_ROLLER + "/" + rollerIndex.toString() + "/pos";
        return callApi(uri, ShellyControlRoller.class);
    }

    public void setRollerTurn(Integer relayIndex, String turnMode) throws ShellyApiException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?go=" + turnMode);
    }

    public void setRollerPos(Integer relayIndex, Integer position) throws ShellyApiException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?go=to_pos&roller_pos="
                + position.toString());
    }

    public void setRollerTimer(Integer relayIndex, Integer timer) throws ShellyApiException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?timer=" + timer.toString());
    }

    public ShellyShortLightStatus getLightStatus(Integer index) throws ShellyApiException {
        String uri = SHELLY_URL_STATUS_LIGHT + "/" + index.toString();
        return callApi(uri, ShellyShortLightStatus.class);
    }

    public ShellyStatusSensor getSensorStatus() throws ShellyApiException {
        Validate.notNull(profile);
        ShellyStatusSensor status = callApi(SHELLY_URL_STATUS, ShellyStatusSensor.class);
        if (profile.isSense) {
            // complete reported data, map C to F or vice versa: C=(F - 32) * 0.5556;
            status.tmp.tC = status.tmp.units.equals(SHELLY_TEMP_CELSIUS) ? status.tmp.value
                    : status.tmp.value / 0.5556 + 32;
            status.tmp.tF = status.tmp.units.equals(SHELLY_TEMP_FAHRENHEIT) ? status.tmp.value
                    : (status.tmp.value + 32) * 0.5556;
        }
        if ((status.charger == null) && (status.externalPower != null)) {
            // SHelly H&T uses external_power, Sense uses charger
            status.charger = status.externalPower != 0;
        }

        return status;
    }

    public void setTimer(Integer index, String timerName, Double value) throws ShellyApiException {
        Validate.notNull(profile);
        String type = SHELLY_CLASS_RELAY;
        if (profile.isRoller) {
            type = SHELLY_CLASS_ROLLER;
        } else if (profile.isLight) {
            type = SHELLY_CLASS_LIGHT;
        }
        String uri = SHELLY_URL_SETTINGS + "/" + type + "/" + index + "?" + timerName + "="
                + ((Integer) value.intValue()).toString();
        request(uri);
    }

    public void setLedStatus(String ledName, Boolean value) throws ShellyApiException {
        request(SHELLY_URL_SETTINGS + "?" + ledName + "=" + (value ? SHELLY_API_TRUE : SHELLY_API_FALSE));
    }

    public ShellySettingsLight getLightSettings() throws ShellyApiException {
        return callApi(SHELLY_URL_SETTINGS_LIGHT, ShellySettingsLight.class);
    }

    public ShellyStatusLight getLightStatus() throws ShellyApiException {
        return callApi(SHELLY_URL_STATUS, ShellyStatusLight.class);
    }

    public void setLightSetting(String parm, String value) throws ShellyApiException {
        request(SHELLY_URL_SETTINGS + "?" + parm + "=" + value);
    }

    /**
     * Change between White and Color Mode
     *
     * @param mode
     * @throws ShellyApiException
     */
    public void setLightMode(String mode) throws ShellyApiException {
        if (!mode.isEmpty() && !profile.mode.equals(mode)) {
            setLightSetting(SHELLY_API_MODE, mode);
            profile.mode = mode;
            profile.inColor = profile.isLight && profile.mode.equalsIgnoreCase(SHELLY_MODE_COLOR);
        }
    }

    /**
     * Set a single light parameter
     *
     * @param lightIndex Index of the light, usually 0 for Bulb and 0..3 for RGBW2.
     * @param parm Name of the parameter (see API spec)
     * @param value The value
     * @throws ShellyApiException
     */
    public void setLightParm(Integer lightIndex, String parm, String value) throws ShellyApiException {
        // Bulb, RGW2: /<color mode>/<light id>?parm?value
        // Dimmer: /light/<light id>?parm=value
        Validate.notNull(profile);
        request(getControlUrlPrefix(lightIndex) + "?" + parm + "=" + value);
    }

    public void setLightParms(Integer lightIndex, Map<String, String> parameters) throws ShellyApiException {
        Validate.notNull(profile);
        String url = getControlUrlPrefix(lightIndex) + "?";
        int i = 0;
        for (String key : parameters.keySet()) {
            if (i > 0) {
                url = url + "&";
            }
            url = url + key + "=" + parameters.get(key);
            i++;
        }
        request(url);
    }

    /**
     * Retrieve the IR Code list from the Shelly Sense device. The list could be customized by the user. It defines the
     * symbolic key code, which gets
     * map into a PRONTO code
     *
     * @return Map of key codes
     * @throws ShellyApiException
     */
    public Map<String, String> getIRCodeList() throws ShellyApiException {
        String result = request(SHELLY_URL_LIST_IR);
        // take pragmatic approach to make the returned JSon into named arrays for Gson parsing
        String keyList = StringUtils.substringAfter(result, "[");
        keyList = StringUtils.substringBeforeLast(keyList, "]");
        keyList = keyList.replaceAll(java.util.regex.Pattern.quote("\",\""), "\", \"name\": \"");
        keyList = keyList.replaceAll(java.util.regex.Pattern.quote("["), "{ \"id\":");
        keyList = keyList.replaceAll(java.util.regex.Pattern.quote("]"), "} ");
        String json = "{\"key_codes\" : [" + keyList + "] }";

        ShellySendKeyList codes = gson.fromJson(json, ShellySendKeyList.class);
        Map<String, String> list = new TreeMap<>();
        for (ShellySenseKeyCode key : codes.keyCodes) {
            list.put(key.id, key.name);
        }
        return list;
    }

    /**
     * Sends a IR key code to the Shelly Sense.
     *
     * @param keyCode A keyCoud could be a symbolic name (as defined in the key map on the device) or a PRONTO Code in
     *            plain or hex64 format
     *
     * @throws ShellyApiException
     * @throws IllegalArgumentException
     */
    public void sendIRKey(String keyCode) throws ShellyApiException, IllegalArgumentException {
        Validate.notNull(profile);
        String type = "";
        if (profile.irCodes.containsKey(keyCode)) {
            type = SHELLY_IR_CODET_STORED;
        } else if ((keyCode.length() > 4) && keyCode.contains(" ")) {
            type = SHELLY_IR_CODET_PRONTO;
        } else {
            type = SHELLY_IR_CODET_PRONTO_HEX;
        }
        String url = SHELLY_URL_SEND_IR + "?type=" + type;
        if (type.equals(SHELLY_IR_CODET_STORED)) {
            url = url + "&" + "id=" + keyCode;
        } else if (type.equals(SHELLY_IR_CODET_PRONTO)) {
            String code = Base64.getEncoder().encodeToString(keyCode.getBytes());
            Validate.notNull(code, "Unable to BASE64 encode the pronto code: " + keyCode);
            url = url + "&" + SHELLY_IR_CODET_PRONTO + "=" + code;
        } else if (type.equals(SHELLY_IR_CODET_PRONTO_HEX)) {
            url = url + "&" + SHELLY_IR_CODET_PRONTO_HEX + "=" + keyCode;
        }
        request(url);
    }

    public void setSenseSetting(String setting, String value) throws ShellyApiException {
        request(SHELLY_URL_SETTINGS + "?" + setting + "=" + value);
    }

    /**
     * Set event callback URLs. Depending on the device different event types are supported. In fact all of them will be
     * redirected to the binding's
     * servlet and act as a trigger to schedule a status update
     *
     * @param ShellyApiException
     * @throws ShellyApiException
     */
    public void setEventURLs() throws ShellyApiException {
        setRelayEvents();
        setDimmerEvents();
        setSensorEventUrls();
    }

    private void setRelayEvents() throws ShellyApiException {
        Validate.notNull(profile);
        if (profile.settings.relays != null) {
            int num = profile.isRoller ? profile.numRollers : profile.numRelays;
            for (int i = 0; i < num; i++) {
                setEventUrls(i);
            }
        }
    }

    private void setDimmerEvents() throws ShellyApiException {
        if (profile.settings.dimmers != null) {
            for (int i = 0; i < profile.settings.dimmers.size(); i++) {
                setEventUrls(i);
            }
        } else if (profile.isLight) {
            setEventUrls(0);
        }
    }

    /**
     * Set event URL for HT (report_url)
     *
     * @param deviceName
     * @throws ShellyApiException
     */
    private void setSensorEventUrls() throws ShellyApiException, ShellyApiException {
        if (profile.supportsSensorUrls && config.eventsSensorReport) {
            logger.debug("{}: Check/set Sensor Reporting URL", thingName);
            String eventUrl = "http://" + config.localIp + ":" + config.httpPort.toString() + SHELLY_CALLBACK_URI + "/"
                    + profile.thingName + "/" + EVENT_TYPE_SENSORDATA;
            request(SHELLY_URL_SETTINGS + "?" + SHELLY_API_EVENTURL_REPORT + "=" + urlEncode(eventUrl));
            if (profile.settingsJson.contains(SHELLY_API_EVENTURL_DARK)) {
                request(SHELLY_URL_SETTINGS + "?" + SHELLY_API_EVENTURL_DARK + "=" + urlEncode(eventUrl));
            }
            if (profile.settingsJson.contains(SHELLY_API_EVENTURL_TWILIGHT)) {
                request(SHELLY_URL_SETTINGS + "?" + SHELLY_API_EVENTURL_TWILIGHT + "=" + urlEncode(eventUrl));
            }
        }
    }

    private void setEventUrls(Integer index) throws ShellyApiException {
        Validate.notNull(profile);
        String lip = config.localIp;
        String localPort = config.httpPort.toString();
        String deviceName = profile.thingName;
        if (profile.isRoller) {
            if (profile.supportsRollerUrls && config.eventsButton) {
                logger.debug("{}: Set Roller event urls", thingName);
                request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_ROLLER,
                        SHELLY_API_EVENTURL_ROLLER_OPEN));
                request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_ROLLER,
                        SHELLY_API_EVENTURL_ROLLER_CLOSE));
                request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_ROLLER,
                        SHELLY_API_EVENTURL_ROLLER_STOP));
            }
        } else {
            if (profile.supportsButtonUrls && config.eventsButton) {
                if (profile.settingsJson.contains(SHELLY_API_EVENTURL_BTN1_ON)) {
                    // 2 set of URLs, e.g. Dimmer
                    logger.debug("{}: Set Dimmer event urls", thingName);

                    request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_LIGHT,
                            SHELLY_API_EVENTURL_BTN1_ON));
                    request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_LIGHT,
                            SHELLY_API_EVENTURL_BTN1_OFF));
                    request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_LIGHT,
                            SHELLY_API_EVENTURL_BTN2_ON));
                    request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_LIGHT,
                            SHELLY_API_EVENTURL_BTN2_OFF));
                } else {
                    // Standard relays: btn_xxx URLs
                    logger.debug("{}: Set Relay event urls", thingName);
                    request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_RELAY,
                            SHELLY_API_EVENTURL_BTN_ON));
                    request(buildSetEventUrl(lip, localPort, deviceName, index, EVENT_TYPE_RELAY,
                            SHELLY_API_EVENTURL_BTN_OFF));
                }
            }

            if (profile.supportsOutUrls && config.eventsSwitch) {
                request(buildSetEventUrl(lip, localPort, deviceName, index,
                        profile.isLight ? EVENT_TYPE_LIGHT : EVENT_TYPE_RELAY, SHELLY_API_EVENTURL_OUT_ON));
                request(buildSetEventUrl(lip, localPort, deviceName, index,
                        profile.isLight ? EVENT_TYPE_LIGHT : EVENT_TYPE_RELAY, SHELLY_API_EVENTURL_OUT_OFF));
            }
            if (profile.supportsPushUrls && config.eventsPush) {
                request(buildSetEventUrl(lip, localPort, deviceName, index,
                        profile.isLight ? EVENT_TYPE_LIGHT : EVENT_TYPE_RELAY, SHELLY_API_EVENTURL_SHORT_PUSH));
                request(buildSetEventUrl(lip, localPort, deviceName, index,
                        profile.isLight ? EVENT_TYPE_LIGHT : EVENT_TYPE_RELAY, SHELLY_API_EVENTURL_LONG_PUSH));
            }
        }
    }

    /**
     * Submit GET request and return response, check for invalid responses
     *
     * @param uri: URI (e.g. "/settings")
     */
    public <T> T callApi(String uri, Class<T> classOfT) throws ShellyApiException {
        String json = "Invalid API result";
        try {
            json = request(uri);
            return gson.fromJson(json, classOfT);
        } catch (JsonSyntaxException e) {
            throw new ShellyApiException("Unable to convert JSON to class " + classOfT.getClass() + ", JSON=" + json);
        }
    }

    private String request(String uri) throws ShellyApiException {
        ShellyApiResult apiResult = new ShellyApiResult();
        try {
            apiResult = innerRequest(HttpMethod.GET, uri);
        } catch (UnknownHostException | MalformedURLException e) {
            throw new ShellyApiException("Unknown host or device is offline");
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            try {
                logger.debug("{}: API call returned {}/{}, retry", thingName, e.getClass(),
                        apiResult.getHttpResponse());
                timeoutErrors++; // count the retries
                apiResult = innerRequest(HttpMethod.GET, uri);
                timeoutsRecovered++; // recoverd
            } catch (UnknownHostException | MalformedURLException | ExecutionException | InterruptedException
                    | TimeoutException e2) {
                logger.debug("{}: API call returned {}/{}, retry", thingName, e.getClass(),
                        apiResult.getHttpResponse());
                throw new ShellyApiException("API Call failed: " + apiResult.getHttpResponse(), e2);
            }
        }
        return apiResult.response;
    }

    @SuppressWarnings("null")
    private ShellyApiResult innerRequest(HttpMethod method, String uri) throws ShellyApiException, UnknownHostException,
            MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
        Request request = null;
        String url = "http://" + config.deviceIp + uri;
        ShellyApiResult apiResult = new ShellyApiResult(method.toString(), url);
        request = httpClient.newRequest(url).method(method.toString()).timeout(SHELLY_API_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);

        if (!config.userId.isEmpty()) {
            String value = config.userId + ":" + config.password;
            request.header(HTTP_HEADER_AUTH,
                    HTTP_AUTH_TYPE_BASIC + " " + Base64.getEncoder().encodeToString(value.getBytes()));
        }
        request.header(HttpHeader.ACCEPT, CONTENT_TYPE_JSON);
        /*
         * if (data != null) {
         * request.content(new StringContentProvider(data, StandardCharsets.UTF_8));
         * }
         * logger.trace("HTTP {} {}, parms={}, data={}, headers={}", method.toString(), url, gs(parms), gs(headers),
         * gs(data));
         */
        logger.trace("{}: HTTP GET for {}", thingName, url);

        // Do request and get response
        ContentResponse contentResponse = request.send();
        apiResult = new ShellyApiResult(contentResponse);
        String response = contentResponse.getContentAsString().replaceAll("\t", "").replaceAll("\r\n", "").trim();

        // validate response, API errors are reported as Json
        logger.trace("HTTP Response: {}", response);
        if (contentResponse.getStatus() != HttpStatus.OK_200) {
            throw new ShellyApiException("API Call failed", apiResult);
        }
        if (response == null || response.isEmpty() || !response.startsWith("{") && !response.startsWith("[")) {
            throw new ShellyApiException("Unexpected HTTP response: " + response);
        }

        return apiResult;
    }

    public String getControlUrlPrefix(Integer id) {
        Validate.notNull(profile);
        String uri = "";
        if (profile.isLight || profile.isDimmer) {
            if (profile.isDuo || profile.isDimmer) {
                // Duo + Dimmer
                uri = SHELLY_URL_CONTROL_LIGHT;
            } else {
                // Bulb + RGBW2
                uri = "/" + (profile.inColor ? SHELLY_MODE_COLOR : SHELLY_MODE_WHITE);
            }
        } else {
            // Roller, Relay
            uri = SHELLY_URL_CONTROL_RELEAY;
        }
        uri = uri + "/" + id.toString();
        logger.trace("{}: Control URL prefix = {}", thingName, uri);
        return uri;
    }

    public static String buildSetEventUrl(String localIp, String localPort, String deviceName, Integer index,
            String deviceType, String urlParm) throws ShellyApiException {
        return SHELLY_URL_SETTINGS + "/" + deviceType + "/" + index + "?" + urlParm + "="
                + buildCallbackUrl(localIp, localPort, deviceName, index, deviceType, urlParm);
    }

    private static String buildCallbackUrl(String localIp, String localPort, String deviceName, Integer index,
            String type, String parameter) throws ShellyApiException {
        String url = "http://" + localIp + ":" + localPort + SHELLY_CALLBACK_URI + "/" + deviceName + "/" + type + "/"
                + index + "?type=" + StringUtils.substringBefore(parameter, "_url");
        return urlEncode(url);
    }

    public int getTimeoutErrors() {
        return timeoutErrors;
    }

    public int getTimeoutsRecovered() {
        return timeoutsRecovered;
    }
}
