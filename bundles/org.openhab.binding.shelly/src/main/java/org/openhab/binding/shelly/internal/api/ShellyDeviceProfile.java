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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
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
    public boolean initialized = false; // true when initialized

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
    public Boolean isDW = false; // true of Door Window sensor

    public Integer minTemp = 0; // Bulb/Duo: Min Light Temp
    public Integer maxTemp = 0; // Bulb/Duo: Max Light Temp

    public Integer updatePeriod = -1;

    public Map<String, String> irCodes = new HashMap<>(); // Sense: list of stored IR codes

    public ShellyDeviceProfile initialize(String thingType, String json) throws ShellyApiException {
        Gson gson = new Gson();

        initialized = false;

        try {
            initFromThingType(thingType);
            settingsJson = json;
            settings = gson.fromJson(json, ShellySettingsGlobal.class);
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            throw new ShellyApiException(e,
                    thingName + ": Unable to transform settings JSON " + e.toString() + ", json='" + json + "'");
        }

        // General settings
        deviceType = ShellyUtils.getString(settings.device.type);
        mac = getString(settings.device.mac);
        hostname = settings.device.hostname != null && !settings.device.hostname.isEmpty()
                ? settings.device.hostname.toLowerCase()
                : "shelly-" + mac.toUpperCase().substring(6, 11);
        mode = !getString(settings.mode).isEmpty() ? getString(settings.mode).toLowerCase() : "";
        hwRev = settings.hwinfo != null ? getString(settings.hwinfo.hwRevision) : "";
        hwBatchId = settings.hwinfo != null ? getString(settings.hwinfo.batchId.toString()) : "";
        fwDate = getString(StringUtils.substringBefore(settings.fw, "/"));
        fwVersion = getString(StringUtils.substringBetween(settings.fw, "/", "@"));
        fwId = getString(StringUtils.substringAfter(settings.fw, "@"));
        discoverable = (settings.discoverable == null) || settings.discoverable;

        inColor = isLight && mode.equalsIgnoreCase(SHELLY_MODE_COLOR);

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

        if (settings.sleepMode != null) {
            updatePeriod = getString(settings.sleepMode.unit).equalsIgnoreCase("m")
                    ? settings.sleepMode.period * 60 + 15 // minutes + 15s
                    : settings.sleepMode.period * 3600 + 60; // hours + 60s
        } else if ((settings.coiot != null) && (settings.coiot.updatePeriod != null)) {
            updatePeriod = 2 * getInteger(settings.coiot.updatePeriod) + 5; // usually 2*15+5s=50sec
        }

        initialized = true;
        return this;
    }

    public boolean containsEventUrl(String eventType) {
        return containsEventUrl(settingsJson, eventType);
    }

    public boolean containsEventUrl(String json, String eventType) {
        String settings = json.toLowerCase();
        return settings.contains((eventType + SHELLY_EVENTURL_SUFFIX).toLowerCase());
    }

    public Boolean isInitialized() {
        return initialized;
    }

    public void initFromThingType(String name) {
        String thingType = (name.contains("-") ? StringUtils.substringBefore(name, "-") : name).toLowerCase().trim();
        if (thingType.isEmpty()) {
            return;
        }

        isPlugS = thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYPLUGS_STR);

        isBulb = thingType.equals(THING_TYPE_SHELLYBULB_STR);
        isDuo = thingType.equals(THING_TYPE_SHELLYDUO_STR) || thingType.equals(THING_TYPE_SHELLYVINTAGE_STR);
        isRGBW2 = thingType.startsWith(THING_TYPE_SHELLYRGBW2_PREFIX);
        hasLed = isPlugS;
        isLight = isBulb || isDuo || isRGBW2;
        minTemp = isBulb ? MIN_COLOR_TEMP_BULB : MIN_COLOR_TEMP_DUO;
        maxTemp = isBulb ? MAX_COLOR_TEMP_BULB : MAX_COLOR_TEMP_DUO;

        boolean isHT = thingType.equals(THING_TYPE_SHELLYHT_STR);
        boolean isFlood = thingType.equals(THING_TYPE_SHELLYFLOOD_STR);
        boolean isSmoke = thingType.equals(THING_TYPE_SHELLYSMOKE_STR);
        isDW = thingType.equals(THING_TYPE_SHELLYDOORWIN_STR);
        isSense = thingType.equals(THING_TYPE_SHELLYSENSE_STR);
        isSensor = isHT || isFlood || isDW || isSmoke || isSense;
        hasBattery = isHT || isFlood || isDW || isSmoke; // we assume that Sense is connected to the charger
    }

    public static ThingUID getThingUID(String serviceName, String mode, boolean unknown) {
        String devid = StringUtils.substringAfterLast(serviceName, "-");
        return new ThingUID(!unknown ? getThingTypeUID(serviceName, mode)
                : getThingTypeUID(THING_TYPE_SHELLYPROTECTED_STR + "-" + devid, mode), devid);
    }

    public static ThingTypeUID getThingTypeUID(String serviceName, String mode) {
        return new ThingTypeUID(BINDING_ID, getThingType(serviceName, mode));
    }

    public static ThingTypeUID getUnknownTTUID() {
        return new ThingTypeUID(BINDING_ID, THING_TYPE_SHELLYPROTECTED_STR);
    }

    public static String getThingType(String hostname, String mode) {
        String name = hostname.toLowerCase();
        String devid = StringUtils.substringAfterLast(name, "-");
        if (devid == null) {
            throw new IllegalArgumentException("Invalid device name format: " + hostname);
        }

        if (name.startsWith(THING_TYPE_SHELLY1PN_STR)) {
            return THING_TYPE_SHELLY1PN_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYEM3_STR)) {
            return THING_TYPE_SHELLYEM3_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYEM_STR)) {
            return THING_TYPE_SHELLYEM_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLY1_STR)) {
            return THING_TYPE_SHELLY1_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLY25_PREFIX)) { // Shelly v2.5
            return mode.equals(SHELLY_MODE_RELAY) ? THING_TYPE_SHELLY25_RELAY_STR : THING_TYPE_SHELLY25_ROLLER_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLY2_PREFIX)) { // Shelly v2
            return mode.equals(SHELLY_MODE_RELAY) ? THING_TYPE_SHELLY2_RELAY_STR : THING_TYPE_SHELLY2_ROLLER_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLY4PRO_STR)) {
            return THING_TYPE_SHELLY4PRO_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYPLUG_STR)) {
            // shellyplug-s needs to be mapped to shellyplugs to follow the schema
            // for the thing types: <thing type>-<mode>
            if (name.startsWith(THING_TYPE_SHELLYPLUGS_STR) || name.contains("-s")) {
                return THING_TYPE_SHELLYPLUGS_STR;
            }
            return THING_TYPE_SHELLYPLUG_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYDIMMER_STR)) {
            return THING_TYPE_SHELLYDIMMER_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYDUO_STR)) {
            return THING_TYPE_SHELLYDUO_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYBULB_STR)) {
            return THING_TYPE_SHELLYBULB_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYRGBW2_PREFIX)) {
            return mode.equals(SHELLY_MODE_COLOR) ? THING_TYPE_SHELLYRGBW2_COLOR_STR : THING_TYPE_SHELLYRGBW2_WHITE_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYHT_STR)) {
            return THING_TYPE_SHELLYHT_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYSMOKE_STR)) {
            return THING_TYPE_SHELLYSMOKE_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYFLOOD_STR)) {
            return THING_TYPE_SHELLYFLOOD_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYDOORWIN_STR)) {
            return THING_TYPE_SHELLYDOORWIN_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYSENSE_STR)) {
            return THING_TYPE_SHELLYSENSE_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYEYE_STR)) {
            return THING_TYPE_SHELLYEYE_STR;
        }
        if (name.startsWith(THING_TYPE_SHELLYPROTECTED_STR)) {
            return THING_TYPE_SHELLYPROTECTED_STR;
        }

        return THING_TYPE_UNKNOWN_STR;
    }
}
