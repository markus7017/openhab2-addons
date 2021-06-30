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
package org.openhab.binding.carnet.internal.api.cnservices;

import static org.openhab.binding.carnet.internal.BindingConstants.*;
import static org.openhab.binding.carnet.internal.api.carnet.CarNetApiConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.carnet.internal.api.ApiBaseService;
import org.openhab.binding.carnet.internal.api.ApiException;
import org.openhab.binding.carnet.internal.api.carnet.CarNetApiBase;
import org.openhab.binding.carnet.internal.handler.VehicleCarNetHandler;
import org.openhab.binding.carnet.internal.provider.ChannelDefinitions.ChannelIdMapEntry;

/**
 * {@link CarNetServiceHonkFlash} implements honk&flash service.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetServiceHonkFlash extends ApiBaseService {
    public CarNetServiceHonkFlash(VehicleCarNetHandler thingHandler, CarNetApiBase api) {
        super(CNAPI_SERVICE_REMOTE_HONK_AND_FLASH, thingHandler, api);
    }

    @Override
    public boolean createChannels(Map<String, ChannelIdMapEntry> channels) throws ApiException {
        // Honk&Flash requires CarFinder service to get geo position
        if (api.isRemoteServiceAvailable(CNAPI_SERVICE_CAR_FINDER)) {
            addChannel(channels, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_FLASH, ITEMT_SWITCH, null, false, false);
            addChannel(channels, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_HONKFLASH, ITEMT_SWITCH, null, false, false);
            addChannel(channels, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_HFDURATION, ITEMT_NUMBER, null, true, false);
            return true;
        }
        return false;
    }
}
