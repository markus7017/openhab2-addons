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

package org.openhab.binding.carnet.internal.discovery;

import static org.openhab.binding.carnet.internal.CarNetBindingConstants.*;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.handler.CarNetAccountHandler;
import org.openhab.binding.carnet.internal.handler.CarNetDeviceListener;
import org.openhab.binding.carnet.internal.handler.CarNetVehicleInformation;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Device discovery creates a thing in the inbox for each vehicle
 * found in the data received from {@link CarNetAccountHandler}.
 *
 * @author Markus Michels - Initial Contribution
 *
 */
@NonNullByDefault
public class CarNetDiscoveryService extends AbstractDiscoveryService implements CarNetDeviceListener {
    private final Logger logger = LoggerFactory.getLogger(CarNetDiscoveryService.class);
    private final CarNetAccountHandler accountHandler;
    private ThingUID bridgeUID;
    private static final int TIMEOUT = 10;

    public CarNetDiscoveryService(CarNetAccountHandler bridgeHandler, Bundle bundle) {
        super(SUPPORTED_THING_TYPES_UIDS, TIMEOUT);
        this.accountHandler = bridgeHandler;
        this.bridgeUID = bridgeHandler.getThing().getUID();
    }

    /**
     * Called by Account Handler for each vehicle found under the account, creates the corresponding vehicle thing
     */
    @Override
    public void informationUpdate(@Nullable List<CarNetVehicleInformation> vehicleList) {
        if (vehicleList == null) {
            return;
        }
        for (CarNetVehicleInformation vehicle : vehicleList) {
            logger.debug("Discovery for [{}]", vehicle.getId());
            Map<String, Object> properties = new TreeMap<String, Object>();
            ThingUID uid = new ThingUID(THING_TYPE_VEHICLE, bridgeUID, vehicle.getId());
            properties.put(PROPERTY_VIN, vehicle.vin);
            properties.put(PROPERTY_MODEL, vehicle.model);
            properties.put(PROPERTY_COLOR, vehicle.color);
            properties.put(PROPERTY_MMI, vehicle.mmi);
            properties.put(PROPERTY_ENGINE, vehicle.engine);
            properties.put(PROPERTY_TRANS, vehicle.transmission);
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID).withProperties(properties)
                    .withRepresentationProperty(PROPERTY_VIN).withLabel(vehicle.model).build();
            thingDiscovered(result);

        }
    }

    @Override
    protected void startScan() {
        try {
            accountHandler.initializeThing();
        } catch (CarNetException e) {
            logger.debug("Discovery failed: {}", e.getMessage());
        }
    }

    public void activate() {
        accountHandler.registerListener(this);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        accountHandler.unregisterListener(this);
    }

    @Override
    public void stateChanged(ThingStatus status, ThingStatusDetail detail, String message) {
    }
}
