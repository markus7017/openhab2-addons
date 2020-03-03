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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.Validate;
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

    private final String thingName;
    private final ShellyBaseHandler thingHandler;
    private Map<String, Object> channelData = new HashMap<>();
    private boolean enabled = false;

    public ShellyChannelCache(ShellyBaseHandler thingHandler) {
        this.thingHandler = thingHandler;
        this.thingName = thingHandler.thingName;
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
    @SuppressWarnings("null")
    public boolean updateChannel(String channelId, State value, Boolean forceUpdate) {
        Validate.notNull(channelData);
        Validate.notNull(channelId);
        Validate.notNull(value, "updateChannel(): value must not be null!");
        try {
            Object current = channelData.get(channelId);
            // logger.trace("{}: Predict channel {}.{} to become {} (type {}).", thingName,
            // group, channel, value, value.getClass());
            if (!enabled || forceUpdate || (current == null) || !current.equals(value)) {
                // For channels that support multiple types (like brightness) a suffix is added
                // this gets removed to get the channelId for updateState
                thingHandler.publishState(channelId, value);
                synchronized (channelData) {
                    if (current == null) {
                        channelData.put(channelId, value);
                    } else {
                        channelData.replace(channelId, value);
                    }
                }
                logger.trace("{}: Channel {} updated with {} (type {}).", thingName, channelId, value,
                        value.getClass());
                return true;
            }
        } catch (NullPointerException e) {
            logger.debug("Unable to update channel {}.{} with {} (type {}): {} ({})", thingName, channelId, value,
                    value.getClass(), e.getMessage(), e.getClass());
        }
        return false;
    }

    public boolean updateChannel(String group, String channel, State value) {
        return updateChannel(mkChannelId(group, channel), value, false);
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
        String key = mkChannelId(group, channel);
        synchronized (channelData) {
            return channelData.get(key);
        }
    }

    public void resetChannel(String channelId) {
        Validate.notNull(channelData);
        Validate.notNull(channelId);
        synchronized (channelData) {
            channelData.remove(channelId);
        }

    }

    public void clear() {
        channelData = new HashMap<>();
    }
}
