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

import org.openhab.binding.shelly.internal.util.ShellyTranslationProvider;

public class ShellyChannelDefinitions {
    public static String ITEM_TYPE_TEMP = "Number:Temperature";
    public static String ITEM_TYPE_PERCENT = "Number:Dimensionless";

    public static String PREFIX_GROUP = "thing-type.shelly.shelly1.group.";
    public static String PREFIX_CHANNEL = "channel-type.shelly.";
    public static String SUFFIX_LABEL = ".label";
    public static String SUFFIX_DESCR = ".description";

    public static class ChannelDefinition {
        String group = "";
        String groupLabel = "";
        String groupDescription = "";

        String channel = "";
        String channelType = "";
        String channelLabel = "";
        String channelDescription = "";

        public ChannelDefinition(ShellyTranslationProvider messages, String group, String channel, String type) {
            this.group = group;
            this.channel = channel;
            this.channelType = type;

            groupLabel = messages.get(PREFIX_GROUP + group + SUFFIX_LABEL);
            groupDescription = messages.get(PREFIX_GROUP + group + SUFFIX_DESCR);
            channelLabel = messages.get(PREFIX_CHANNEL + channel + SUFFIX_LABEL);
            channelDescription = messages.get(PREFIX_CHANNEL + channel + SUFFIX_DESCR);
        }

        public String getChanneId() {
            return group + "#" + channel;
        }
    }

    public static class ChannelMap {
        private final HashMap<String, ChannelDefinition> map = new HashMap<>();

        private ChannelMap add(ChannelDefinition def) {
            map.put(def.getChanneId(), def);
            return this;
        }
    }

    private static final ChannelMap channelDefinitions = new ChannelMap();

    public ShellyChannelDefinitions(ShellyTranslationProvider messages) {
        // Device: Internal Temp
        channelDefinitions
                .add(new ChannelDefinition(messages, CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ITEMP, ITEM_TYPE_TEMP))

                // Addon with external sensors
                .add(new ChannelDefinition(messages, CHANNEL_GROUP_SENSOR, CHANNEL_ESENDOR_TEMP1, ITEM_TYPE_TEMP))
                .add(new ChannelDefinition(messages, CHANNEL_GROUP_SENSOR, CHANNEL_ESENDOR_TEMP2, ITEM_TYPE_TEMP))
                .add(new ChannelDefinition(messages, CHANNEL_GROUP_SENSOR, CHANNEL_ESENDOR_TEMP3, ITEM_TYPE_TEMP))
                .add(new ChannelDefinition(messages, CHANNEL_GROUP_SENSOR, CHANNEL_ESENDOR_HUMIDITY,
                        ITEM_TYPE_PERCENT));
    }
}
