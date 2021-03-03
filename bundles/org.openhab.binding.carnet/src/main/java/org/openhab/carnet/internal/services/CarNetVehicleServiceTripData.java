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
package org.openhab.carnet.internal.services;

import static org.openhab.binding.carnet.internal.CarNetBindingConstants.*;
import static org.openhab.binding.carnet.internal.CarNetUtils.*;
import static org.openhab.binding.carnet.internal.api.CarNetApiConstants.CNAPI_SERVICE_TRIPDATA;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.api.CarNetApi;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetTripData;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetTripData.CarNetTripDataList.CarNetTripDataEntry;
import org.openhab.binding.carnet.internal.handler.CarNetCombinedConfig;
import org.openhab.binding.carnet.internal.handler.CarNetVehicleHandler;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper.ChannelIdMapEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CarNetVehicleServiceTripData} implements the drip data service (short-term + long-term).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetVehicleServiceTripData extends CarNetVehicleBaseService {
    private final Logger logger = LoggerFactory.getLogger(CarNetVehicleServiceTripData.class);

    public CarNetVehicleServiceTripData(CarNetVehicleHandler thingHandler, CarNetApi api) {
        super(thingHandler, api);
        serviceId = CNAPI_SERVICE_TRIPDATA;
    }

    @Override
    public boolean createChannels(Map<String, ChannelIdMapEntry> channels) throws CarNetException {
        boolean updated = update("shortTerm", channels);
        return updated | update("longTerm", channels);
    }

    private boolean createChannels(Map<String, ChannelIdMapEntry> ch, String type, int index) {
        boolean a = false;
        String group = CHANNEL_GROUP_TRIP_PRE + type + index;
        a |= addChannel(ch, group, CHANNEL_TRIP_TIME, ITEMT_DATETIME, null, false, true);
        a |= addChannel(ch, group, CHANNEL_TRIP_AVG_ELCON, ITEMT_ENERGY, SmartHomeUnits.KILOWATT_HOUR, false, true);
        a |= addChannel(ch, group, CHANNEL_TRIP_AVG_FUELCON, ITEMT_VOLUME, SmartHomeUnits.LITRE, false, true);
        a |= addChannel(ch, group, CHANNEL_TRIP_AVG_SPEED, ITEMT_SPEED, SIUnits.KILOMETRE_PER_HOUR, false, true);
        a |= addChannel(ch, group, CHANNEL_TRIP_START_MIL, ITEMT_DISTANCE, KILOMETRE, false, true);
        a |= addChannel(ch, group, CHANNEL_TRIP_MILAGE, ITEMT_DISTANCE, KILOMETRE, false, true);
        a |= addChannel(ch, group, CHANNEL_TRIP_OVR_MILAGE, ITEMT_DISTANCE, KILOMETRE, true, true);
        return a;
    }

    @Override
    public boolean serviceUpdate() throws CarNetException {
        boolean updated = update("shortTerm", null);
        return updated | update("longTerm", null);
    }

    private boolean update(String type, @Nullable Map<String, ChannelIdMapEntry> channels) throws CarNetException {
        boolean updated = false;
        CarNetTripData std = api.getTripData(type);
        if (std != null) {
            Collections.sort(std.tripDataList.tripData, Collections.reverseOrder(new Comparator<CarNetTripDataEntry>() {
                @Override
                public int compare(CarNetTripDataEntry a, CarNetTripDataEntry b) {
                    return a.timestamp.compareTo(b.timestamp);
                }
            }));

            CarNetCombinedConfig config = getConfig();
            boolean shortTerm = type.contains("short");
            int numTrips = shortTerm ? config.vehicle.numTripShort : config.vehicle.numTripLong;

            int i = 0; // latest first
            int l = 1;
            while ((i < std.tripDataList.tripData.size()) && (l <= numTrips)) {
                if (channels != null) {
                    createChannels(channels, shortTerm ? CHANNEL_TRIP_SHORT : CHANNEL_TRIP_LONG, l);
                } else {
                    String group = (shortTerm ? CHANNEL_GROUP_STRIP : CHANNEL_GROUP_LTRIP) + l;
                    CarNetTripDataEntry entry = std.tripDataList.tripData.get(i);
                    if (entry != null) {
                        double fuel = getDouble(entry.averageFuelConsumption) / 10.0; // convert dL to l
                        updated |= updateChannel(group, CHANNEL_TRIP_TIME, getDateTime(getString(entry.timestamp)));
                        updated |= updateChannel(group, CHANNEL_TRIP_AVG_FUELCON, new DecimalType(fuel), 1,
                                SmartHomeUnits.LITRE);
                        updated |= updateChannel(group, CHANNEL_TRIP_AVG_ELCON,
                                new DecimalType(getInteger(entry.averageElectricEngineConsumption) * 100 / 1000), 3,
                                SmartHomeUnits.KILOWATT_HOUR); // convert kw per km to kw/h per 100km
                        updated |= updateChannel(group, CHANNEL_TRIP_AVG_SPEED, getDecimal(entry.averageSpeed), 1,
                                SIUnits.KILOMETRE_PER_HOUR);
                        updated |= updateChannel(group, CHANNEL_TRIP_START_MIL, getDecimal(entry.startMileage),
                                KILOMETRE);
                        updated |= updateChannel(group, CHANNEL_TRIP_MILAGE, getDecimal(entry.mileage), KILOMETRE);
                        updated |= updateChannel(group, CHANNEL_TRIP_OVR_MILAGE, getDecimal(entry.overallMileage),
                                KILOMETRE);
                    }
                }
                i++;
                l++;
            }
        }
        return updated;
    }
}
