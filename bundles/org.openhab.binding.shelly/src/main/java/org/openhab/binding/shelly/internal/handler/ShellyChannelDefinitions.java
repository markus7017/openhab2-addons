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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyControlRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsEMeter;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsMeter;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyStatusRelay;
import org.openhab.binding.shelly.internal.util.ShellyTranslationProvider;

/**
 * The {@link ShellyChannelDefinitions} defines channel information for dynamically created channels. Those will be
 * added on the first thing status update
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyChannelDefinitions {

    private static final ChannelMap channelDefinitions = new ChannelMap();

    private static String CHGR_METER = CHANNEL_GROUP_METER;
    private static String CHGR_SENSOR = CHANNEL_GROUP_SENSOR;

    public ShellyChannelDefinitions(ShellyTranslationProvider m) {
        // Device: Internal Temp
        channelDefinitions
                // Device
                .add(new ShellyChannel(m, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ITEMP, "deviceTemp", ITEM_TYPE_TEMP))

                // Addon with external sensors
                .add(new ShellyChannel(m, CHGR_SENSOR, CHANNEL_ESENDOR_TEMP1, "sensorExtTemp", ITEM_TYPE_TEMP))
                .add(new ShellyChannel(m, CHGR_SENSOR, CHANNEL_ESENDOR_TEMP2, "sensorExtTemp", ITEM_TYPE_TEMP))
                .add(new ShellyChannel(m, CHGR_SENSOR, CHANNEL_ESENDOR_TEMP3, "sensorExtTemp", ITEM_TYPE_TEMP))
                .add(new ShellyChannel(m, CHGR_SENSOR, CHANNEL_ESENDOR_HUMIDITY, "sensorExtHum", ITEM_TYPE_PERCENT))

                // Power Meter
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_METER_CURRENTWATTS, "meterWatts", ITEM_TYPE_POWER))
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_METER_TOTALKWH, "meterTotal", ITEM_TYPE_ENERGY))
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_METER_LASTMIN1, "lastPower1", ITEM_TYPE_ENERGY))
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_METER_LASTMIN2, "lastPower2", ITEM_TYPE_ENERGY))
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_METER_LASTMIN3, "lastPower3", ITEM_TYPE_ENERGY))

                // EMeter
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_EMETER_TOTALRET, "meterReturned", ITEM_TYPE_ENERGY))
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_EMETER_REACTWATTS, "meterReactive", ITEM_TYPE_POWER))
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_EMETER_VOLTAGE, "meterVoltage", ITEM_TYPE_VOLT))
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_EMETER_CURRENT, "meterCurrent", ITEM_TYPE_AMP))
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_EMETER_PFACTOR, "meterPowerFactor", ITEM_TYPE_NUMBER))

                // Last update for Meters
                .add(new ShellyChannel(m, CHGR_METER, CHANNEL_LAST_UPDATE, "timestamp", ITEM_TYPE_DATETIME))

        ;
    }

    public static ShellyChannel getDefinition(String channelName) throws IllegalArgumentException {
        String group = StringUtils.substringBefore(channelName, "#");
        String channel = StringUtils.substringAfter(channelName, "#");
        if (group.contains(CHANNEL_GROUP_METER)) {
            group = CHANNEL_GROUP_METER; // map meter1..n to meter
        } else if (group.contains(CHANNEL_GROUP_RELAY_CONTROL)) {
            group = CHANNEL_GROUP_RELAY_CONTROL; // map meter1..n to meter
        }
        return channelDefinitions.get(group + "#" + channel);
    }

    /**
     * Auto-create relay channels depending on relay type/mode
     *
     * @return ArrayList<Channel> of channels to be added to the thing
     */
    public static Map<String, Channel> createRelayChannels(final Thing thing, final ShellyStatusRelay relays) {
        Map<String, Channel> add = new TreeMap<>();
        // Only some devices report the internal device temp
        addChannel(thing, add, relays.temperature != null, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ITEMP);

        // Shelly 1/1PM Addon
        if (relays.extTemperature != null) {
            addChannel(thing, add, relays.extTemperature.sensor1 != null, CHGR_SENSOR, CHANNEL_ESENDOR_TEMP1);
            addChannel(thing, add, relays.extTemperature.sensor2 != null, CHGR_SENSOR, CHANNEL_ESENDOR_TEMP2);
            addChannel(thing, add, relays.extTemperature.sensor3 != null, CHGR_SENSOR, CHANNEL_ESENDOR_TEMP2);
        }
        if (relays.extHumidity != null) {
            addChannel(thing, add, relays.extHumidity.sensor1 != null, CHGR_SENSOR, CHANNEL_ESENDOR_HUMIDITY);
        }
        return add;
    }

    public static Map<String, Channel> createRollerChannels(Thing thing, final ShellyControlRoller roller) {
        Map<String, Channel> add = new TreeMap<>();

        // No dynamic channels so far, maybe added in the future

        return add;
    }

    public static Map<String, Channel> createMeterChannels(Thing thing, final ShellySettingsMeter meter, String group) {
        Map<String, Channel> newChannels = new TreeMap<>();
        addChannel(thing, newChannels, meter.power != null, group, CHANNEL_METER_CURRENTWATTS);
        addChannel(thing, newChannels, meter.total != null, group, CHANNEL_METER_TOTALKWH);
        if (meter.counters != null) {
            addChannel(thing, newChannels, meter.counters[0] != null, group, CHANNEL_METER_LASTMIN1);
            addChannel(thing, newChannels, meter.counters[1] != null, group, CHANNEL_METER_LASTMIN2);
            addChannel(thing, newChannels, meter.counters[2] != null, group, CHANNEL_METER_LASTMIN3);
        }
        return newChannels;
    }

    public static Map<String, Channel> createEMeterChannels(final Thing thing, final ShellySettingsEMeter emeter,
            String group) {
        Map<String, Channel> newChannels = new TreeMap<>();
        addChannel(thing, newChannels, emeter.power != null, group, CHANNEL_METER_CURRENTWATTS);
        addChannel(thing, newChannels, emeter.total != null, group, CHANNEL_METER_TOTALKWH);
        addChannel(thing, newChannels, emeter.totalReturned != null, group, CHANNEL_EMETER_TOTALRET);
        addChannel(thing, newChannels, emeter.reactive != null, group, CHANNEL_EMETER_REACTWATTS);
        addChannel(thing, newChannels, emeter.voltage != null, group, CHANNEL_EMETER_VOLTAGE);
        addChannel(thing, newChannels, emeter.current != null, group, CHANNEL_EMETER_CURRENT);
        addChannel(thing, newChannels, emeter.pf != null, group, CHANNEL_EMETER_PFACTOR);
        return newChannels;
    }

    private static void addChannel(Thing thing, Map<String, Channel> newChannels, boolean supported, String group,
            String channelName) throws IllegalArgumentException {
        if (supported) {
            final String channelId = group + "#" + channelName;
            final ShellyChannel channelDef = getDefinition(channelId);
            final ChannelUID channelUID = new ChannelUID(thing.getUID(), channelId);
            final ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, channelDef.typeId);

            // Channel channel = ChannelBuilder.create(channelUID, channelId).withType(channelTypeUID)
            // .withLabel(channelDef.label).withDescription(channelDef.description).build();
            Channel channel = ChannelBuilder.create(channelUID, channelDef.itemType).withType(channelTypeUID).build();
            newChannels.put(channelId, channel);
        }
    }

    public static String ITEM_TYPE_NUMBER = "Number";
    public static String ITEM_TYPE_STRING = "String";
    public static String ITEM_TYPE_DATETIME = "DateTime";
    public static String ITEM_TYPE_TEMP = "Number:Temperature";
    public static String ITEM_TYPE_POWER = "Number:Power";
    public static String ITEM_TYPE_ENERGY = "Number:Energy";
    public static String ITEM_TYPE_VOLT = "Number:ElectricPotential";
    public static String ITEM_TYPE_AMP = "Number:ElectricPotential";
    public static String ITEM_TYPE_PERCENT = "Number:Dimensionless";

    public static String PREFIX_GROUP = "definitions.shelly.group.";
    public static String PREFIX_CHANNEL = "channel-type.shelly.";
    public static String SUFFIX_LABEL = ".label";
    public static String SUFFIX_DESCR = ".description";

    public class ShellyChannel {
        private final ShellyTranslationProvider messages;
        public String group = "";
        public String groupLabel = "";
        public String groupDescription = "";

        public String channel = "";
        public String label = "";
        public String description = "";
        public String itemType = "";
        public String typeId = "";
        public String category = "";
        public Set<String> tags = new HashSet<>();

        public ShellyChannel(ShellyTranslationProvider messages, String group, String channel, String typeId,
                String itemType, String... category) {
            this.messages = messages;
            this.group = group;
            this.channel = channel;
            this.itemType = itemType;
            this.typeId = typeId;

            groupLabel = getText(PREFIX_GROUP + group + SUFFIX_LABEL);
            groupDescription = getText(PREFIX_GROUP + group + SUFFIX_DESCR);
            label = getText(PREFIX_CHANNEL + channel + SUFFIX_LABEL);
            description = getText(PREFIX_CHANNEL + channel + SUFFIX_DESCR);
        }

        public String getChanneId() {
            return group + "#" + channel;
        }

        private String getText(String key) {
            String text = messages.get(key);
            return text != null ? text : "";
        }
    }

    public static class ChannelMap {
        private final HashMap<String, ShellyChannel> map = new HashMap<>();

        private ChannelMap add(ShellyChannel def) {
            map.put(def.getChanneId(), def);
            return this;
        }

        public ShellyChannel get(String channelName) throws IllegalArgumentException {
            if (channelName.contains("#")) {
                return map.get(channelName);
            }
            for (HashMap.Entry<String, ShellyChannel> entry : map.entrySet()) {
                ShellyChannel def = entry.getValue();
                if (def.channel.contains("#" + channelName)) {
                    return def;
                }
            }
            throw new IllegalArgumentException("Channel definition for " + channelName + " not found!");
        }
    }
}
