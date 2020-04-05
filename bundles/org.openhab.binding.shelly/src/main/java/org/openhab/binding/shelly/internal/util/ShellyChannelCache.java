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

package org.openhab.binding.shelly.internal.util;

import static org.openhab.binding.shelly.internal.util.ShellyUtils.mkChannelId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.shelly.internal.handler.ShellyBaseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ShellyChannelCache} implements a caching layer for channel updates.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyChannelCache {
    private final Logger logger = LoggerFactory.getLogger(ShellyChannelCache.class);

    private final ShellyBaseHandler thingHandler;
    private final Map<String, Object> channelData = new ConcurrentHashMap<>();
    private String thingName = "";
    private boolean enabled = false;

    public ShellyChannelCache(ShellyBaseHandler thingHandler) {
        this.thingHandler = thingHandler;
        setThingName(thingHandler.thingName);
    }

    public void setThingName(String thingName) {
        this.thingName = thingName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    /**
     * Update one channel. Use Channel Cache to avoid unnecessary updates (and avoid
     * messing up the log with those updates)
     *
     * @param channelId Channel id
     * @param value Value (State)
     * @param forceUpdate true: ignore cached data, force update; false check cache of changed data
     * @return true, if successful
     */
    public boolean updateChannel(String channelId, State value, Boolean forceUpdate) {
        try {
            Object current = null;
            if (channelData.containsKey(channelId)) {
                current = channelData.get(channelId);
            }
            if (!enabled || forceUpdate || (current == null) || !current.equals(value)) {
                // For channels that support multiple types (like brightness) a suffix is added
                // this gets removed to get the channelId for updateState
                thingHandler.publishState(channelId, value);
                /*
                 * if (current == null) {
                 * channelData.put(channelId, value);
                 * } else {
                 * channelData.replace(channelId, value);
                 * }
                 */
                channelData.replace(channelId, value);

                logger.trace("{}: Channel {} updated with {} (type {}).", thingName, channelId, value,
                        value.getClass());
                return true;
            }
        } catch (IllegalArgumentException e) {
            logger.debug("{}: Unable to update channel {} with {} (type {}): {} ({})", thingName, channelId, value,
                    value.getClass().toString(), ShellyUtils.getMessage(e), e.getClass().toString());
        }
        return false;
    }

    public boolean updateChannel(String group, String channel, State value) {
        return updateChannel(mkChannelId(group, channel), value, false);
    }

    public boolean updateChannel(String channelId, State value) {
        return updateChannel(channelId, value, false);
    }

    /**
     * Get a value from the Channel Cache
     *
     * @param group Channel Group
     * @param channel Channel Name
     * @return the data from that channel
     */
    @Nullable
    public Object getValue(String group, String channel) {
        return getValue(mkChannelId(group, channel));
    }

    @Nullable
    public Object getValue(String channelId) {
        return channelData.get(channelId);
    }

    public void resetChannel(String channelId) {
        /*
         * if (channelData.containsKey(channelId)) {
         * channelData.remove(channelId);
         * }
         */
        channelData.remove(channelId);
    }

    public void clear() {
        channelData.clear();
    }
}
