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

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.EVENT_TYPE_LIGHT;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.EVENT_TYPE_RELAY;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.EVENT_TYPE_ROLLER;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.EVENT_TYPE_SENSORDATA;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.HTTP_AUTH_TYPE_BASIC;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.HTTP_HEADER_AUTH;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_CALLBACK_URI;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_IR_CODET_PRONTO;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_IR_CODET_PRONTO_HEX;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_IR_CODET_STORED;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_CONTROL_LIGHT;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_CONTROL_RELEAY;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_CONTROL_ROLLER;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_DEVINFO;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_LIST_IR;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_SEND_IR;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_SETTINGS;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_SETTINGS_LIGHT;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_STATUS;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_STATUS_LIGHT;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.SHELLY_URL_STATUS_RELEAY;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.*;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
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
import org.openhab.binding.shelly.internal.util.ShellyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import io.netty.handler.codec.http.HttpMethod;

/**
 * {@link ShellyHttpApi} wraps the Shelly REST API and provides various low level function to access the device api (not
 * cloud api).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyHttpApi {
    private final Logger logger = LoggerFactory.getLogger(ShellyHttpApi.class);
    private final @Nullable HttpClient httpClient;
    private final ShellyThingConfiguration config;
    private final String thingName;
    private int timeoutErrors = 0;
    private int timeoutsRecovered = 0;
    private Gson gson = new Gson();

    private @Nullable ShellyDeviceProfile profile;

    public ShellyHttpApi(String thingName, ShellyThingConfiguration config, @Nullable HttpClient httpClient) {
        Validate.notNull(config, thingName + ": Shelly API: Config must not be null!");
        this.config = config;
        this.thingName = thingName;
        this.httpClient = httpClient;
    }

    @Nullable
    public ShellySettingsDevice getDevInfo() throws ShellyException {
        String json = request(SHELLY_URL_DEVINFO);
        logger.debug("{}: Shelly device info : {}", thingName, json);
        return gson.fromJson(json, ShellySettingsDevice.class);
    }

    /**
     * Initialize the device profile
     *
     * @param thingType Type of DEVICE as returned from the thing properties (based on discovery)
     * @return Initialized ShellyDeviceProfile
     * @throws IOException
     */
    @SuppressWarnings("null")
    @Nullable
    public ShellyDeviceProfile getDeviceProfile(String thingType) throws ShellyException {
        String json = request(SHELLY_URL_SETTINGS);
        if (json.contains("\"type\":\"SHDM-1\"")) {
            logger.trace("{}: Detected a Shelly Dimmer: fix Json (replace lights[] tag with dimmers[]", thingName);
            json = fixDimmerJson(json);
        }

        // Map settings to device profile for Light and Sense
        profile = ShellyDeviceProfile.initialize(thingType, json);
        Validate.notNull(profile, "Unable to parse device settings: " + json);

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

    /**
     * Get generic device settings/status. Json returned from API will be mapped to a Gson object
     *
     * @return Device settings/status as ShellySettingsStatus object
     * @throws IOException
     */
    public ShellySettingsStatus getStatus() throws ShellyException {
        String json = request(SHELLY_URL_STATUS);
        ShellySettingsStatus status = gson.fromJson(json, ShellySettingsStatus.class);
        Validate.notNull(status);
        status.json = json;
        return status;
    }

    @Nullable
    public ShellyStatusRelay getRelayStatus(Integer relayIndex) throws ShellyException {
        String result = request(SHELLY_URL_STATUS_RELEAY + "/" + relayIndex.toString());
        return gson.fromJson(result, ShellyStatusRelay.class);
    }

    public void setRelayTurn(Integer id, String turnMode) throws ShellyException {
        Validate.notNull(profile);
        request(getControlUrlPrefix(id) + "?" + SHELLY_LIGHT_TURN + "=" + turnMode.toLowerCase());
    }

    public void setBrightness(Integer id, Integer brightness, boolean autoOn) throws ShellyException {
        String turn = autoOn ? SHELLY_LIGHT_TURN + "=" + SHELLY_API_ON + "&" : "";
        request(getControlUrlPrefix(id) + "?" + turn + "brightness=" + brightness.toString());
    }

    @SuppressWarnings("null")
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

    @Nullable
    public ShellyControlRoller getRollerStatus(Integer rollerIndex) throws ShellyException {
        String result = request(SHELLY_URL_CONTROL_ROLLER + "/" + rollerIndex.toString() + "/pos");
        return gson.fromJson(result, ShellyControlRoller.class);
    }

    public void setRollerTurn(Integer relayIndex, String turnMode) throws ShellyException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?go=" + turnMode);
    }

    public void setRollerPos(Integer relayIndex, Integer position) throws ShellyException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?go=to_pos&roller_pos="
                + position.toString());
    }

    public void setRollerTimer(Integer relayIndex, Integer timer) throws ShellyException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?timer=" + timer.toString());
    }

    @Nullable
    public ShellyShortLightStatus getLightStatus(Integer index) throws ShellyException {
        String result = request(SHELLY_URL_STATUS_LIGHT + "/" + index.toString());
        return gson.fromJson(result, ShellyShortLightStatus.class);
    }

    @SuppressWarnings("null")
    public ShellyStatusSensor getSensorStatus() throws ShellyException {
        Validate.notNull(profile);
        ShellyStatusSensor status = gson.fromJson(request(SHELLY_URL_STATUS), ShellyStatusSensor.class);
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

    @SuppressWarnings("null")
    public void setTimer(Integer index, String timerName, Double value) throws ShellyException {
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

    public void setLedStatus(String ledName, Boolean value) throws ShellyException {
        request(SHELLY_URL_SETTINGS + "?" + ledName + "=" + (value ? SHELLY_API_TRUE : SHELLY_API_FALSE));
    }

    @Nullable
    public ShellySettingsLight getLightSettings() throws ShellyException {
        String result = request(SHELLY_URL_SETTINGS_LIGHT);
        return gson.fromJson(result, ShellySettingsLight.class);
    }

    @Nullable
    public ShellyStatusLight getLightStatus() throws ShellyException {
        String result = request(SHELLY_URL_STATUS);
        return gson.fromJson(result, ShellyStatusLight.class);
    }

    public void setLightSetting(String parm, String value) throws ShellyException {
        request(SHELLY_URL_SETTINGS + "?" + parm + "=" + value);
    }

    /**
     * Change between White and Color Mode
     *
     * @param mode
     * @throws IOException
     */
    @SuppressWarnings("null")
    public void setLightMode(String mode) throws ShellyException {
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
     * @throws IOException
     */
    public void setLightParm(Integer lightIndex, String parm, String value) throws ShellyException {
        // Bulb, RGW2: /<color mode>/<light id>?parm?value
        // Dimmer: /light/<light id>?parm=value
        Validate.notNull(profile);
        request(getControlUrlPrefix(lightIndex) + "?" + parm + "=" + value);
    }

    public void setLightParms(Integer lightIndex, Map<String, String> parameters) throws ShellyException {
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
     * @throws IOException
     */
    public Map<String, String> getIRCodeList() throws ShellyException {
        String result = request(SHELLY_URL_LIST_IR);
        // take pragmatic approach to make the returned JSon into named arrays for Gson parsing
        String keyList = StringUtils.substringAfter(result, "[");
        keyList = StringUtils.substringBeforeLast(keyList, "]");
        keyList = keyList.replaceAll(java.util.regex.Pattern.quote("\",\""), "\", \"name\": \"");
        keyList = keyList.replaceAll(java.util.regex.Pattern.quote("["), "{ \"id\":");
        keyList = keyList.replaceAll(java.util.regex.Pattern.quote("]"), "} ");
        String json = "{\"key_codes\" : [" + keyList + "] }";

        ShellySendKeyList codes = gson.fromJson(json, ShellySendKeyList.class);
        Map<String, String> list = new HashMap<String, String>();
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
     * @throws IOException
     * @throws IllegalArgumentException
     */
    @SuppressWarnings("null")
    public void sendIRKey(String keyCode) throws ShellyException, IllegalArgumentException {
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

    public void setSenseSetting(String setting, String value) throws ShellyException {
        request(SHELLY_URL_SETTINGS + "?" + setting + "=" + value);
    }

    /**
     * Set event callback URLs. Depending on the device different event types are supported. In fact all of them will be
     * redirected to the binding's
     * servlet and act as a trigger to schedule a status update
     *
     * @param deviceName
     * @throws IOException
     */
    public void setEventURLs() throws ShellyException {
        setRelayEvents();
        setDimmerEvents();
        setSensorEventUrls();
    }

    @SuppressWarnings("null")
    private void setRelayEvents() throws ShellyException {
        Validate.notNull(profile);
        if (profile.settings.relays != null) {
            int num = profile.isRoller ? profile.numRollers : profile.numRelays;
            for (int i = 0; i < num; i++) {
                setEventUrls(i);
            }
        }
    }

    @SuppressWarnings("null")
    private void setDimmerEvents() throws ShellyException {
        Validate.notNull(profile);
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
     * @throws IOException
     */
    @SuppressWarnings("null")
    private void setSensorEventUrls() throws ShellyException, ShellyException {
        try {
            Validate.notNull(profile);
            if (profile.supportsSensorUrls && config.eventsSensorReport) {
                logger.debug("{}: Check/set Sensor Reporting URL", thingName);
                String eventUrl = "http://" + config.localIp + ":" + config.httpPort.toString() + SHELLY_CALLBACK_URI
                        + "/" + profile.thingName + "/" + EVENT_TYPE_SENSORDATA;
                request(SHELLY_URL_SETTINGS + "?" + SHELLY_API_EVENTURL_REPORT + "=" + urlEncode(eventUrl));
                if (profile.settingsJson.contains(SHELLY_API_EVENTURL_DARK)) {
                    request(SHELLY_URL_SETTINGS + "?" + SHELLY_API_EVENTURL_DARK + "=" + urlEncode(eventUrl));
                }
                if (profile.settingsJson.contains(SHELLY_API_EVENTURL_TWILIGHT)) {
                    request(SHELLY_URL_SETTINGS + "?" + SHELLY_API_EVENTURL_TWILIGHT + "=" + urlEncode(eventUrl));
                }
            }
        } catch (IOException e) {
            throw new ShellyException(getString(e.getMessage()));
        }
    }

    @SuppressWarnings("null")
    private void setEventUrls(Integer index) throws ShellyException {
        try {
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
        } catch (IOException e) {
            throw new ShellyException(getString(e.getMessage()));
        }
    }

    /**
     * Submit GET request and return response, check for invalid responses
     *
     * @param uri: URI (e.g. "/settings")
     */
    @SuppressWarnings("null")
    private String request(String uri) throws ShellyException {
        final String method = HttpMethod.GET.toString();
        Request request = null;
        String url = "http://" + config.deviceIp + uri;
        try {
            ShellyApiResult apiResult = new ShellyApiResult(method, url);
            request = httpClient.newRequest(url).method(method).timeout(SHELLY_API_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            Map<String, String> headers = new TreeMap<>();
            if (!config.userId.isEmpty()) {
                String value = config.userId + ":" + config.password;
                headers.put(HTTP_HEADER_AUTH,
                        HTTP_AUTH_TYPE_BASIC + " " + Base64.getEncoder().encodeToString(value.getBytes()));
            }
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    Validate.notNull(h.getKey());
                    String value = h.getValue();
                    if ((value != null) && !value.isEmpty()) {
                        request.header(h.getKey(), h.getValue());
                    }
                }
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
            Validate.notNull(response);

            // validate response, API errors are reported as Json
            logger.trace("HTTP Response: {}", response);
            if (contentResponse.getStatus() != HttpStatus.OK_200) {
                throw new ShellyException("API Call failed", apiResult);
            }
            if (response.isEmpty()) {
                throw new ShellyException("Invalid result received from API, maybe URL problem", apiResult);
            }
            return response;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            ShellyApiResult apiResult = new ShellyApiResult(request, e);
            throw new ShellyException("API call failed!", apiResult, e);
        }

        /*
         * String result = "EMPTY";
         * String message = "";
         * String type = "";
         * boolean retry = false;
         * try {
         * result = innerRequest(uri);
         * if (result.isEmpty()) {
         * logger.debug("{}: Empty http result, retry!", thingName);
         * retry = true;
         * }
         * } catch (IOException e) {
         * type = getExceptionType(e);
         * message = getString(e);
         * if (getString(type.toLowerCase()).contains("timeout") || message.contains("Connection reset")
         * || type.contains("InterruptedException")) {
         * timeoutErrors++;
         * logger.debug("{}: Shelly API timeout # {} ({}), retry", thingName, timeoutErrors, type);
         * retry = true;
         * }
         * }
         * if (((profile != null) && profile.hasBattery || result.contains(APIERR_HTTP_401_UNAUTHORIZED))) {
         * retry = false;
         * }
         * if (retry) {
         * try {
         * // retry to recover
         * result = innerRequest(uri);
         * timeoutsRecovered++;
         * logger.debug("{}: Shelly API timeout recovered", thingName);
         * } catch (IOException e) {
         * type = getExceptionType(e);
         * message = "Shelly API timeout: " + getString(e);
         * }
         * }
         * if (message.isEmpty() && (result.equals("EMPTY") || result.isEmpty())) {
         * message = "Empty response, Timeout?";
         * }
         * if (!message.isEmpty()) {
         * throw new IOException("Shelly API error: " + message + " (" + type + "), uri=" + uri);
         * }
         * return result;
         * }
         *
         * private String innerRequest(String uri) throws IOException {
         * String httpResponse = "ERROR";
         * String url = "http://" + config.deviceIp + uri;
         * logger.trace("{}: HTTP GET for {}", thingName, url);
         *
         * Properties headers = new Properties();
         * if (!config.userId.isEmpty()) {
         * String value = config.userId + ":" + config.password;
         * headers.put(HTTP_HEADER_AUTH,
         * HTTP_AUTH_TYPE_BASIC + " " + Base64.getEncoder().encodeToString(value.getBytes()));
         * }
         *
         * httpResponse = HttpUtil.executeUrl(HttpMethod.GET, url, headers, null, "", SHELLY_API_TIMEOUT_MS);
         * logger.trace("{}: HTTP response: {}", thingName, httpResponse);
         * Validate.notNull(httpResponse, "httpResponse must not be null");
         * // all api responses are returning the result in Json format. If we are getting
         * // something else it must
         * // be an error message, e.g. http result code
         * if (httpResponse.contains(APIERR_HTTP_401_UNAUTHORIZED)) {
         * throw new IOException(
         * APIERR_HTTP_401_UNAUTHORIZED + ", set/correct userid and password in the thing/binding config");
         * }
         * if (!httpResponse.startsWith("{") && !httpResponse.startsWith("[")) {
         * throw new IOException("Unexpected response: " + httpResponse);
         * }
         *
         * return httpResponse;
         */
    }

    public static String buildSetEventUrl(String localIp, String localPort, String deviceName, Integer index,
            String deviceType, String urlParm) throws IOException {
        return SHELLY_URL_SETTINGS + "/" + deviceType + "/" + index + "?" + urlParm + "="
                + buildCallbackUrl(localIp, localPort, deviceName, index, deviceType, urlParm);
    }

    private static String buildCallbackUrl(String localIp, String localPort, String deviceName, Integer index,
            String type, String parameter) throws IOException {
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

    @SuppressWarnings("null")
    private String getExceptionType(@Nullable ShellyException e) {
        if ((e == null) || (e.getClass() == null) || e.getClass().toString().isEmpty()) {
            return "";
        }

        String msg = StringUtils.substringAfterLast(e.getClass().toString(), ".");
        if (msg != null) {
            return msg;
        }
        return e.getCause().toString();
    }

}
