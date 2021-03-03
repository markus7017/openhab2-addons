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
package org.openhab.binding.carnet.internal.services;

import static org.openhab.binding.carnet.internal.CarNetBindingConstants.*;
import static org.openhab.binding.carnet.internal.CarNetUtils.*;
import static org.openhab.binding.carnet.internal.api.CarNetApiConstants.CNAPI_SERVICE_REMOTE_PRETRIP_CLIMATISATION;

import java.math.BigDecimal;
import java.util.Map;

import javax.measure.IncommensurableException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.api.CarNetApi;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNClimater.CarNetClimaterStatus;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNClimater.CarNetClimaterStatus.CNClimaterStatus.CarNetClimaterStatusData;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNClimater.CarNetClimaterStatus.CNClimaterStatus.CarNetClimaterStatusData.CNClimaterElementState.CarNetClimaterZoneStateList;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CNClimater.CarNetClimaterStatus.CNClimaterStatus.CarNetClimaterStatusData.CarNetClimaterZoneState;
import org.openhab.binding.carnet.internal.handler.CarNetVehicleHandler;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper.ChannelIdMapEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CarNetVehicleServiceClimater} implements climater service.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetVehicleServiceClimater extends CarNetVehicleBaseService {
    private final Logger logger = LoggerFactory.getLogger(CarNetVehicleServiceClimater.class);

    public CarNetVehicleServiceClimater(CarNetVehicleHandler thingHandler, CarNetApi api) {
        super(thingHandler, api);
        serviceId = CNAPI_SERVICE_REMOTE_PRETRIP_CLIMATISATION;
    }

    @Override
    public boolean createChannels(Map<String, ChannelIdMapEntry> ch) throws CarNetException {
        try {
            CarNetClimaterStatus cs = api.getClimaterStatus();
            if (cs.status.climatisationStatusData != null) {
                addChannel(ch, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_CLIMATER, ITEMT_SWITCH, null, false, false);
                addChannel(ch, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_WINHEAT, ITEMT_SWITCH, null, false, false);
                addChannel(ch, CHANNEL_GROUP_CONTROL, CHANNEL_CONTROL_PREHEAT, ITEMT_SWITCH, null, false, false);
                addChannel(ch, CHANNEL_GROUP_CLIMATER, CHANNEL_CLIMATER_TARGET_TEMP, ITEMT_TEMP, SIUnits.CELSIUS, false,
                        false);
                addChannel(ch, CHANNEL_GROUP_CLIMATER, CHANNEL_CLIMATER_HEAT_SOURCE, ITEMT_STRING, null, true, true);
                addChannel(ch, CHANNEL_GROUP_CLIMATER, CHANNEL_CLIMATER_GEN_STATE, ITEMT_STRING, null, false, true);
                addChannel(ch, CHANNEL_GROUP_CLIMATER, CHANNEL_CLIMATER_FL_STATE, ITEMT_SWITCH, null, true, true);
                addChannel(ch, CHANNEL_GROUP_CLIMATER, CHANNEL_CLIMATER_FR_STATE, ITEMT_SWITCH, null, true, true);
                addChannel(ch, CHANNEL_GROUP_CLIMATER, CHANNEL_CLIMATER_RL_STATE, ITEMT_SWITCH, null, true, true);
                addChannel(ch, CHANNEL_GROUP_CLIMATER, CHANNEL_CLIMATER_RR_STATE, ITEMT_SWITCH, null, true, true);
                addChannel(ch, CHANNEL_GROUP_CLIMATER, CHANNEL_CLIMATER_MIRROR_HEAT, ITEMT_SWITCH, null, false, true);
                return true;
            }
        } catch (CarNetException e) {
            // Service not available
        }
        return false;
    }

    @Override
    public boolean serviceUpdate() throws CarNetException {
        boolean updated = false;
        try {
            CarNetClimaterStatus cs = api.getClimaterStatus();
            String group = CHANNEL_GROUP_CLIMATER;
            if (cs.settings != null) {
                if (cs.settings.heaterSource != null) {
                    // convert temp from dK to C
                    Double temp = getDouble(cs.settings.targetTemperature.content).doubleValue();
                    BigDecimal bd = new BigDecimal(
                            DKELVIN.getConverterToAny(SIUnits.CELSIUS).convert(temp).doubleValue());
                    updated |= updateChannel(group, CHANNEL_CLIMATER_TARGET_TEMP,
                            toQuantityType(bd.doubleValue(), 1, SIUnits.CELSIUS));
                    if (cs.settings.heaterSource != null) {
                        updated |= updateChannel(group, CHANNEL_CLIMATER_HEAT_SOURCE,
                                getStringType(cs.settings.heaterSource.content));
                    }
                }
            }

            if (cs.status != null) {
                if (cs.status.climatisationStatusData != null) {
                    CarNetClimaterStatusData sd = cs.status.climatisationStatusData;
                    if (sd.climatisationState != null) {
                        updated |= updateChannel(group, CHANNEL_CLIMATER_GEN_STATE,
                                getStringType(sd.climatisationState.content));
                    }
                    if (sd.climatisationElementStates != null) {
                        updateZoneStates(sd.climatisationElementStates.zoneStates);
                        if (sd.climatisationElementStates.isMirrorHeatingActive != null) {
                            updated |= updateChannel(group, CHANNEL_CLIMATER_MIRROR_HEAT,
                                    getOnOff(sd.climatisationElementStates.isMirrorHeatingActive.content));
                        }
                    }
                }
            }
        } catch (IncommensurableException e) {
            logger.debug("IncommensurableException ignored");
        }
        return updated;
    }

    private boolean updateZoneStates(@Nullable CarNetClimaterZoneStateList zoneList) {
        if (zoneList != null) {
            for (CarNetClimaterZoneState zs : zoneList.zoneState) {
                updateChannel(CHANNEL_GROUP_CLIMATER, getString(zs.value.position), getOnOff(zs.value.isActive));
            }
            return true;
        }
        return false;
    }
}
