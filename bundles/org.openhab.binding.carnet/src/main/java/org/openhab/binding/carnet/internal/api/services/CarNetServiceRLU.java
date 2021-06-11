/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.carnet.internal.api.services;

import static org.openhab.binding.carnet.internal.CarNetBindingConstants.*;
import static org.openhab.binding.carnet.internal.CarNetUtils.*;
import static org.openhab.binding.carnet.internal.api.CarNetApiConstants.CNAPI_SERVICE_REMOTE_LOCK_UNLOCK;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.api.CarNetApiBase;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNEluActionHistory.CarNetRluHistory;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNEluActionHistory.CarNetRluHistory.CarNetRluLockActionList.CarNetRluLockAction;
import org.openhab.binding.carnet.internal.api.CarNetChannelIdMapper.ChannelIdMapEntry;
import org.openhab.binding.carnet.internal.handler.CarNetVehicleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CarNetServiceRLU} implements remote vehicle lock/unlock and history.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetServiceRLU extends CarNetBaseService {
    private final Logger logger = LoggerFactory.getLogger(CarNetServiceRLU.class);

    public CarNetServiceRLU(CarNetVehicleHandler thingHandler, CarNetApiBase api) {
        super(CNAPI_SERVICE_REMOTE_LOCK_UNLOCK, thingHandler, api);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().vehicle.numRluHistory > 0 && super.isEnabled();
    }

    @Override
    public boolean createChannels(Map<String, ChannelIdMapEntry> channels) throws CarNetException {
        addChannel(channels, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_LOCK, ITEMT_SWITCH, null, false, false);
        for (int i = 0; i < getConfig().vehicle.numRluHistory; i++) {
            String group = CHANNEL_GROUP_RLUHIST + (i + 1);
            addChannel(channels, group, CHANNEL_RLUHIST_OP, ITEMT_STRING, null, false, true);
            addChannel(channels, group, CHANNEL_RLUHIST_TS, ITEMT_DATETIME, null, false, true);
            addChannel(channels, group, CHANNEL_RLUHIST_RES, ITEMT_STRING, null, false, true);
        }
        return true;
    }

    @Override
    public boolean serviceUpdate() throws CarNetException {
        boolean updated = false;
        CarNetRluHistory hist = api.getRluActionHistory();
        Collections.sort(hist.actions.action, Collections.reverseOrder(new Comparator<CarNetRluLockAction>() {
            @Override
            public int compare(CarNetRluLockAction a, CarNetRluLockAction b) {
                return a.timestamp.compareTo(b.timestamp);
            }
        }));

        int i = 0;
        int count = getConfig().vehicle.numRluHistory;
        for (CarNetRluLockAction entry : hist.actions.action) {
            if (++i > count) {
                break;
            }
            String group = CHANNEL_GROUP_RLUHIST + i;
            updated |= updateChannel(group, CHANNEL_RLUHIST_TS, getDateTime(getString(entry.timestamp)));
            updated |= updateChannel(group, CHANNEL_RLUHIST_OP, getStringType(entry.operation));
            updated |= updateChannel(group, CHANNEL_RLUHIST_RES, getStringType(entry.rluResult));
        }
        return updated;
    }
}
