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

import java.util.Map;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.api.CarNetApi;
import org.openhab.binding.carnet.internal.config.CarNetCombinedConfig;
import org.openhab.binding.carnet.internal.handler.CarNetAccountHandler;
import org.openhab.binding.carnet.internal.handler.CarNetVehicleHandler;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper.ChannelIdMapEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CarNetAccountHandler} implements the base for vehicle services.
 *
 * @author Markus Michels - Initial contribution
 *
 */
@NonNullByDefault
public class CarNetVehicleBaseService {
    private final Logger logger = LoggerFactory.getLogger(CarNetVehicleBaseService.class);
    protected final CarNetApi api;
    protected final CarNetVehicleHandler thingHandler;
    protected final CarNetIChanneldMapper idMapper;
    protected final String thingId;
    protected String serviceId = "";
    protected boolean enabled = true;

    public CarNetVehicleBaseService(CarNetVehicleHandler thingHandler, CarNetApi api) {
        this.thingHandler = thingHandler;
        this.thingId = thingHandler.thingId;
        this.idMapper = thingHandler.getIdMapper();
        this.api = api;
    }

    public String getServiceId() {
        return serviceId;
    }

    // will be overload by service
    public boolean createChannels(Map<String, ChannelIdMapEntry> ch) throws CarNetException {
        return false;
    }

    public void disable() {
        enabled = false;
    }

    public boolean update() throws CarNetException {
        try {
            if (!enabled) {
                return false;
            }
            return serviceUpdate();
        } catch (CarNetException e) {
            int httpCode = e.getApiResult().httpCode;
            if (e.isSecurityException()) {
                enabled = false;
                logger.debug("Service {} is not available!", serviceId);
            } else if (httpCode == HttpStatus.NO_CONTENT_204) {
                logger.debug("Service {} returned NO_CONTENT (204)", serviceId);
            }
        }
        return false;
    }

    // will be overload by service
    public boolean serviceUpdate() throws CarNetException {
        return false;
    }

    public boolean addChannel(Map<String, ChannelIdMapEntry> channels, String group, String channel, String itemType,
            @Nullable Unit<?> unit, boolean advanced, boolean readOnly) {
        return thingHandler.addChannel(channels, group, channel, itemType, unit, advanced, readOnly);
    }

    protected boolean updateChannel(String group, String channel, State value) {
        return thingHandler.updateChannel(group, channel, value);
    }

    protected boolean updateChannel(String group, String channel, State value, Unit<?> unit) {
        return thingHandler.updateChannel(group, channel, value, unit);
    }

    protected boolean updateChannel(String group, String channel, State value, int digits, Unit<?> unit) {
        return thingHandler.updateChannel(group, channel, value, digits, unit);
    }

    protected CarNetCombinedConfig getConfig() {
        return thingHandler.getThingConfig();
    }
}
