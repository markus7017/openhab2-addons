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
import static org.openhab.binding.shelly.internal.util.ShellyUtils.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.shelly.internal.ShellyBindingConstants;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsGlobal;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.util.ShellyUtils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link ShellyDeviceProfile} creates a device profile based on the settings returned from the API's /settings
 * call. This is used to be more dynamic in controlling the device, but also to overcome some issues in the API (e.g.
 * RGBW2 returns "no meter" even it has one)
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyDeviceProfile {
    public Boolean initialized = false; // true when initialized

    public String thingName = "";
    public String deviceType = "";

    public String settingsJson = "";
    public ShellySettingsGlobal settings = new ShellySettingsGlobal();
    public ShellySettingsStatus status = new ShellySettingsStatus();

    public String hostname = "";
    public String mode = "";
    public Boolean discoverable = true;

    public String hwRev = "";
    public String hwBatchId = "";
    public String mac = "";
    public String fwId = "";
    public String fwVersion = "";
    public String fwDate = "";

    public Boolean hasRelays = false; // true if it has at least 1 power meter
    public Integer numRelays = 0; // number of relays/outputs
    public Integer numRollers = 0; // number of Rollers, usually 1
    public Boolean isRoller = false; // true for Shelly2 in roller mode
    public Boolean isDimmer = false; // true for a Shelly Dimmer (SHDM-1)
    public Boolean isPlugS = false; // true if it is a Shelly Plug S

    public Integer numMeters = 0;
    public Boolean isEMeter = false; // true for ShellyEM/EM3

    public Boolean isLight = false; // true if it is a Shelly Bulb/RGBW2
    public Boolean isBulb = false; // true only if it is a Bulb
    public Boolean isDuo = false; // true only if it is a Duo
    public Boolean isRGBW2 = false; // true only if it a a RGBW2
    public Boolean inColor = false; // true if bulb/rgbw2 is in color mode
    public Boolean hasLed = false; // true if battery device

    public Boolean isSensor = false; // true for HT & Smoke
    public Boolean hasBattery = false; // true if battery device
    public Boolean isSense = false; // true if thing is a Shelly Sense

    public Integer minTemp = 0; // Bulb/Duo: Min Light Temp
    public Integer maxTemp = 0; // Bulb/Duo: Max Light Temp

    public Map<String, String> irCodes = new HashMap<>(); // Sense: list of stored IR codes

    public Boolean supportsButtonUrls = false; // true if the btn_xxx urls are supported
    public Boolean supportsOutUrls = false; // true if the out_xxx urls are supported
    public Boolean supportsPushUrls = false; // true if sensor report_url is supported
    public Boolean supportsRollerUrls = false; // true if the roller_xxx urls are supported
    public Boolean supportsSensorUrls = false; // true if sensor report_url is supported

    @SuppressWarnings("null")
    public ShellyDeviceProfile initialize(String thingType, String json) throws ShellyApiException {
        Gson gson = new Gson();

        initialized = false;

        try {
            initFromThingType(thingType);
            settingsJson = json;
            settings = gson.fromJson(json, ShellySettingsGlobal.class);
            Validate.notNull(settings, "Converted device settings must not be null!\nsettings=" + json);
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            throw new ShellyApiException(
                    thingName + ": Unable to transform settings JSON e.toString, json='" + json + "'");
        }

        // General settings
        deviceType = ShellyUtils.getString(settings.device.type);
        mac = getString(settings.device.mac);
        hostname = settings.device.hostname != null && !settings.device.hostname.isEmpty()
                ? settings.device.hostname.toLowerCase()
                : "shelly-" + mac.toUpperCase().substring(6, 11);
        mode = getString(settings.mode) != null ? getString(settings.mode).toLowerCase() : "";
        hwRev = settings.hwinfo != null ? getString(settings.hwinfo.hwRevision) : "";
        hwBatchId = settings.hwinfo != null ? getString(settings.hwinfo.batchId.toString()) : "";
        fwDate = getString(StringUtils.substringBefore(settings.fw, "/"));
        fwVersion = getString(StringUtils.substringBetween(settings.fw, "/", "@"));
        fwId = getString(StringUtils.substringAfter(settings.fw, "@"));
        discoverable = (settings.discoverable == null) || settings.discoverable;

        inColor = isLight && mode.equalsIgnoreCase(SHELLY_MODE_COLOR);
        minTemp = isBulb ? MIN_COLOR_TEMP_BULB : MIN_COLOR_TEMP_DUO;
        maxTemp = isBulb ? MAX_COLOR_TEMP_BULB : MAX_COLOR_TEMP_DUO;

        numRelays = !isLight ? getInteger(settings.device.numOutputs) : 0;
        if ((numRelays > 0) && (settings.relays == null)) {
            numRelays = 0;
        }
        hasRelays = (numRelays > 0) || isDimmer;
        numRollers = getInteger(settings.device.numRollers);

        isEMeter = settings.emeters != null;
        numMeters = !isEMeter ? getInteger(settings.device.numMeters) : getInteger(settings.device.numEMeters);
        if ((numMeters == 0) && isLight) {
            // RGBW2 doesn't report, but has one
            numMeters = inColor ? 1 : getInteger(settings.device.numOutputs);
        }
        isDimmer = deviceType.equalsIgnoreCase(SHELLYDT_DIMMER);
        isRoller = mode.equalsIgnoreCase(SHELLY_MODE_ROLLER);

        supportsButtonUrls = settingsJson.contains(SHELLY_API_EVENTURL_BTN_ON)
                || settingsJson.contains(SHELLY_API_EVENTURL_BTN1_ON)
                || settingsJson.contains(SHELLY_API_EVENTURL_BTN2_ON);
        supportsOutUrls = settingsJson.contains(SHELLY_API_EVENTURL_OUT_ON);
        supportsPushUrls = settingsJson.contains(SHELLY_API_EVENTURL_SHORT_PUSH);
        supportsRollerUrls = settingsJson.contains(SHELLY_API_EVENTURL_ROLLER_OPEN);
        supportsSensorUrls = settingsJson.contains(SHELLY_API_EVENTURL_REPORT);

        initialized = true;
        return this;
    }

    public Boolean isInitialized() {
        return initialized;
    }

    public void initFromThingType(String name) {
        String thingType = name.contains("-") ? StringUtils.substringBefore(name, "-") : name;

        isPlugS = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYPLUGS_STR);

        isBulb = thingType.equalsIgnoreCase(THING_TYPE_SHELLYBULB_STR);
        isDuo = thingType.equalsIgnoreCase(THING_TYPE_SHELLYDUO_STR)
                || thingType.equalsIgnoreCase(THING_TYPE_SHELLYVINTAGE_STR);
        isRGBW2 = thingType.equalsIgnoreCase(THING_TYPE_SHELLYRGBW2_COLOR_STR)
                || thingType.equalsIgnoreCase(THING_TYPE_SHELLYRGBW2_WHITE_STR);
        hasLed = isPlugS;
        isLight = isBulb || isDuo || isRGBW2;

        boolean isHT = thingType.equalsIgnoreCase(THING_TYPE_SHELLYHT_STR);
        boolean isFlood = thingType.equalsIgnoreCase(THING_TYPE_SHELLYFLOOD_STR);
        boolean isDW = thingType.equalsIgnoreCase(THING_TYPE_SHELLYDOORWIN_STR);
        boolean isSmoke = thingType.equalsIgnoreCase(THING_TYPE_SHELLYSMOKE_STR);
        isSense = thingType.equalsIgnoreCase(THING_TYPE_SHELLYSENSE_STR);
        isSensor = isHT | isFlood | isDW | isSmoke | isSense;
        hasBattery = isHT || isFlood || isDW || isSmoke; // we assume that Sense is connected to the charger
    }
}
