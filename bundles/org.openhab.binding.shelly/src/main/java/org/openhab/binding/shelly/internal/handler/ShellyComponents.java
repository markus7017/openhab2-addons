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
package org.openhab.binding.shelly.internal.handler;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.*;

import java.io.IOException;

import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsEMeter;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsMeter;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyStatusSensor;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;

/***
 * The{@link ShellyComponents} implements updates for supplemental components
 * Meter will be used by Relay + Light; Sensor is part of H&T, Flood, Door Window, Sense
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyComponents {

    /**
     * Update device status
     *
     * @param th Thing Handler instance
     * @param profile ShellyDeviceProfile
     */
    public static boolean updateDeviceStatus(ShellyBaseHandler th, ShellySettingsStatus status) {
        if (!th.areChannelsCreated()) {
            th.updateChannelDefinitions(
                    ShellyChannelDefinitions.createDeviceChannels(th.getThing(), th.getProfile(), status));
        }

        Integer rssi = getInteger(status.wifiSta.rssi);
        th.updateChannel(CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_UPTIME,
                toQuantityType(new Double(getLong(status.uptime)), DIGITS_TEMP, SmartHomeUnits.SECOND));
        th.updateChannel(CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_RSSI, mapSignalStrength(rssi));
        if (status.tmp != null) {
            th.updateChannel(CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ITEMP,
                    toQuantityType(getDouble(status.tmp.tC), DIGITS_TEMP, SIUnits.CELSIUS));
        } else if (status.temperature != null) {
            th.updateChannel(CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ITEMP,
                    toQuantityType(getDouble(status.temperature), DIGITS_TEMP, SIUnits.CELSIUS));
        }

        return false; // device status never triggers update
    }

    /**
     * Update Meter channel
     *
     * @param th Thing Handler instance
     * @param profile ShellyDeviceProfile
     * @param status Last ShellySettingsStatus
     */
    public static boolean updateMeters(ShellyBaseHandler th, ShellySettingsStatus status) {
        Validate.notNull(th);
        ShellyDeviceProfile profile = th.getProfile();

        Double accumulatedWatts = 0.0;

        boolean updated = false;
        if ((profile.numMeters > 0) && ((status.meters != null) || (status.emeters != null))) {
            if (!profile.isRoller) {
                th.logger.trace("{}: Updating {} {}meter(s)", th.thingName, profile.numMeters.toString(),
                        !profile.isEMeter ? "standard " : "e-");

                // In Relay mode we map eacher meter to the matching channel group
                int m = 0;
                if (!profile.isEMeter) {
                    for (ShellySettingsMeter meter : status.meters) {
                        Integer meterIndex = m + 1;
                        if (getBool(meter.isValid) || profile.isLight) { // RGBW2-white doesn't report das flag
                                                                         // correctly in white mode
                            String groupName = "";
                            if (profile.numMeters > 1) {
                                groupName = CHANNEL_GROUP_METER + meterIndex.toString();
                            } else {
                                groupName = CHANNEL_GROUP_METER;
                            }

                            if (!th.areChannelsCreated()) {
                                th.updateChannelDefinitions(
                                        ShellyChannelDefinitions.createMeterChannels(th.getThing(), meter, groupName));
                            }

                            updated |= th.updateChannel(groupName, CHANNEL_METER_CURRENTWATTS,
                                    toQuantityType(getDouble(meter.power), DIGITS_WATT, SmartHomeUnits.WATT));
                            accumulatedWatts += getDouble(meter.power);

                            // convert Watt/Min to kw/h
                            if (meter.total != null) {
                                updated |= th.updateChannel(groupName, CHANNEL_METER_TOTALKWH, toQuantityType(
                                        getDouble(meter.total) / 60 / 1000, DIGITS_KWH, SmartHomeUnits.KILOWATT_HOUR));
                            }
                            if (meter.counters != null) {
                                updated |= th.updateChannel(groupName, CHANNEL_METER_LASTMIN1,
                                        toQuantityType(getDouble(meter.counters[0]), DIGITS_WATT, SmartHomeUnits.WATT));
                                updated |= th.updateChannel(groupName, CHANNEL_METER_LASTMIN2,
                                        toQuantityType(getDouble(meter.counters[1]), DIGITS_WATT, SmartHomeUnits.WATT));
                                updated |= th.updateChannel(groupName, CHANNEL_METER_LASTMIN3,
                                        toQuantityType(getDouble(meter.counters[2]), DIGITS_WATT, SmartHomeUnits.WATT));
                            }
                            th.updateChannel(groupName, CHANNEL_LAST_UPDATE,
                                    getTimestamp(getString(profile.settings.timezone), getLong(meter.timestamp)));
                            m++;
                        }
                    }
                } else {
                    for (ShellySettingsEMeter emeter : status.emeters) {
                        Integer meterIndex = m + 1;
                        if (getBool(emeter.isValid)) {
                            String groupName = profile.numMeters > 1 ? CHANNEL_GROUP_METER + meterIndex.toString()
                                    : CHANNEL_GROUP_METER;
                            if (!th.areChannelsCreated()) {
                                th.updateChannelDefinitions(ShellyChannelDefinitions.createEMeterChannels(th.getThing(),
                                        emeter, groupName));
                            }

                            // convert Watt/Hour tok w/h
                            updated |= th.updateChannel(groupName, CHANNEL_METER_CURRENTWATTS,
                                    toQuantityType(getDouble(emeter.power), DIGITS_WATT, SmartHomeUnits.WATT));
                            accumulatedWatts += getDouble(emeter.power);
                            updated |= th.updateChannel(groupName, CHANNEL_METER_TOTALKWH, toQuantityType(
                                    getDouble(emeter.total) / 1000, DIGITS_KWH, SmartHomeUnits.KILOWATT_HOUR));
                            updated |= th.updateChannel(groupName, CHANNEL_EMETER_TOTALRET, toQuantityType(
                                    getDouble(emeter.totalReturned) / 1000, DIGITS_KWH, SmartHomeUnits.KILOWATT_HOUR));
                            updated |= th.updateChannel(groupName, CHANNEL_EMETER_REACTWATTS,
                                    toQuantityType(getDouble(emeter.reactive), DIGITS_WATT, SmartHomeUnits.WATT));
                            updated |= th.updateChannel(groupName, CHANNEL_EMETER_VOLTAGE,
                                    toQuantityType(getDouble(emeter.voltage), DIGITS_VOLT, SmartHomeUnits.VOLT));

                            if (emeter.current != null) {
                                // Shelly EM3
                                updated |= th.updateChannel(groupName, CHANNEL_EMETER_CURRENT,
                                        toQuantityType(getDouble(emeter.current), DIGITS_VOLT, SmartHomeUnits.AMPERE));
                                updated |= th.updateChannel(groupName, CHANNEL_EMETER_PFACTOR, getDecimal(emeter.pf));
                            }

                            if (updated) {
                                th.updateChannel(groupName, CHANNEL_LAST_UPDATE, getTimestamp());
                            }
                            m++;
                        }
                    }
                }
            } else {
                // In Roller Mode we accumulate all meters to a single set of meters
                th.logger.trace("{}: Updating roller meter", th.thingName);
                Double currentWatts = 0.0;
                Double totalWatts = 0.0;
                Double lastMin1 = 0.0;
                Double lastMin2 = 0.0;
                Double lastMin3 = 0.0;
                Long timestamp = 0l;
                String groupName = CHANNEL_GROUP_METER;
                for (ShellySettingsMeter meter : status.meters) {
                    if (meter.isValid) {
                        currentWatts += getDouble(meter.power);
                        totalWatts += getDouble(meter.total);
                        if (meter.counters != null) {
                            lastMin1 += getDouble(meter.counters[0]);
                            lastMin2 += getDouble(meter.counters[1]);
                            lastMin3 += getDouble(meter.counters[2]);
                        }
                        if (getLong(meter.timestamp) > timestamp) {
                            timestamp = getLong(meter.timestamp); // newest one
                        }
                    }
                }
                // Create channels for 1 Meter
                if (!th.areChannelsCreated()) {
                    th.updateChannelDefinitions(ShellyChannelDefinitions.createMeterChannels(th.getThing(),
                            status.meters.get(0), groupName));
                }

                updated |= th.updateChannel(groupName, CHANNEL_METER_LASTMIN1,
                        toQuantityType(getDouble(lastMin1), DIGITS_WATT, SmartHomeUnits.WATT));
                updated |= th.updateChannel(groupName, CHANNEL_METER_LASTMIN2,
                        toQuantityType(getDouble(lastMin2), DIGITS_WATT, SmartHomeUnits.WATT));
                updated |= th.updateChannel(groupName, CHANNEL_METER_LASTMIN3,
                        toQuantityType(getDouble(lastMin3), DIGITS_WATT, SmartHomeUnits.WATT));

                // convert totalWatts into kw/h
                totalWatts = totalWatts / (60.0 * 10000.0);
                updated |= th.updateChannel(groupName, CHANNEL_METER_CURRENTWATTS,
                        toQuantityType(getDouble(currentWatts), DIGITS_WATT, SmartHomeUnits.WATT));
                updated |= th.updateChannel(groupName, CHANNEL_METER_TOTALKWH,
                        toQuantityType(getDouble(totalWatts), DIGITS_KWH, SmartHomeUnits.KILOWATT_HOUR));
                accumulatedWatts += currentWatts;

                if (updated) {
                    th.updateChannel(groupName, CHANNEL_LAST_UPDATE,
                            getTimestamp(getString(profile.settings.timezone), timestamp));
                }
            }
            th.updateChannel(CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ACCUWATTS,
                    toQuantityType(accumulatedWatts, DIGITS_WATT, SmartHomeUnits.WATT));
        }

        return updated;
    }

    /**
     * Update Sensor channel
     *
     * @param th Thing Handler instance
     * @param profile ShellyDeviceProfile
     * @param status Last ShellySettingsStatus
     *
     * @throws IOException
     */
    @SuppressWarnings("null")
    public static boolean updateSensors(ShellyBaseHandler th, ShellySettingsStatus status) throws ShellyApiException {
        Validate.notNull(th);
        ShellyDeviceProfile profile = th.getProfile();

        boolean updated = false;
        if (profile.isSensor || profile.hasBattery) {
            th.logger.debug("{}: Updating sensor", th.thingName);
            ShellyStatusSensor sdata = th.api.getSensorStatus();

            if (sdata != null) {
                if (!th.areChannelsCreated()) {
                    th.updateChannelDefinitions(ShellyChannelDefinitions.createSensorChannels(th.getThing(), sdata));
                }

                if (sdata.actReasons != null) {
                    boolean changed = th.updateChannel(CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_WAKEUP,
                            getStringType(sdata.actReasons[0]));
                    updated |= changed;

                }
                if ((sdata.contact != null) && sdata.contact.isValid) {
                    // Shelly DW: “sensor”:{“state”:“open”, “is_valid”:true},
                    th.logger.debug("{}: Updating DW state with {}", th.thingName, getString(sdata.contact.state));
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_STATE,
                            getString(sdata.contact.state).equalsIgnoreCase(SHELLY_API_DWSTATE_OPEN)
                                    ? OpenClosedType.OPEN
                                    : OpenClosedType.CLOSED);
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_ERROR,
                            getStringType(sdata.sensorError));
                }
                if ((sdata.tmp != null) && getBool(sdata.tmp.isValid)) {
                    th.logger.trace("{}: Updating temperature", th.thingName);
                    DecimalType temp = getString(sdata.tmp.units).toUpperCase().equals(SHELLY_TEMP_CELSIUS)
                            ? getDecimal(sdata.tmp.tC)
                            : getDecimal(sdata.tmp.tF);
                    if (getString(sdata.tmp.units).toUpperCase().equals(SHELLY_TEMP_FAHRENHEIT)) {
                        // convert Fahrenheit to Celsius
                        temp = new DecimalType((temp.floatValue() - 32) * 5 / 9.0);
                    }
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP,
                            toQuantityType(temp.doubleValue(), DIGITS_TEMP, SIUnits.CELSIUS));
                }
                if (sdata.hum != null) {
                    th.logger.trace("{}: Updating humidity", th.thingName);
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_HUM,
                            toQuantityType(getDouble(sdata.hum.value), DIGITS_PERCENT, SmartHomeUnits.PERCENT));
                }
                if ((sdata.lux != null) && getBool(sdata.lux.isValid)) {
                    // “lux”:{“value”:30, “illumination”: “dark”, “is_valid”:true},
                    th.logger.trace("{}: Updating lux", th.thingName);
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_LUX,
                            toQuantityType(getDouble(sdata.lux.value), DIGITS_LUX, SmartHomeUnits.LUX));
                    if (sdata.lux.illumination != null) {
                        updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_ILLUM,
                                getStringType(sdata.lux.illumination));
                    }
                }
                if (sdata.accel != null) {
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_VIBRATION,
                            getBool(sdata.accel.vibration) ? OnOffType.ON : OnOffType.OFF);
                }
                if (sdata.flood != null) {
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_FLOOD, getOnOff(sdata.flood));
                }
                if (sdata.bat != null) { // no update for Sense
                    th.logger.trace("{}: Updating battery", th.thingName);
                    updated |= th.updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LEVEL,
                            toQuantityType(getDouble(sdata.bat.value), DIGITS_PERCENT, SmartHomeUnits.PERCENT));
                    updated |= th.updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_VOLT,
                            toQuantityType(getDouble(sdata.bat.voltage), DIGITS_VOLT, SmartHomeUnits.VOLT));
                    updated |= th.updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LOW,
                            getDouble(sdata.bat.value) < th.config.lowBattery ? OnOffType.ON : OnOffType.OFF);
                    if (getDouble(sdata.bat.value) < th.config.lowBattery) {
                        th.postEvent(ALARM_TYPE_LOW_BATTERY, false);
                    }
                }
                if (sdata.motion != null) {
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION, getOnOff(sdata.motion));
                }
                if (sdata.charger != null) {
                    updated |= th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_CHARGER, getOnOff(sdata.charger));

                }

                if (updated) {
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_LAST_UPDATE, getTimestamp());
                }
            }
        }
        return updated;
    }
}
