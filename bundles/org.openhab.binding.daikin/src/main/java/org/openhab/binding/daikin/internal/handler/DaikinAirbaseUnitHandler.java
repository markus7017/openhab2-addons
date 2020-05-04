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
package org.openhab.binding.daikin.internal.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.StateOption;
import org.openhab.binding.daikin.internal.DaikinBindingConstants;
import org.openhab.binding.daikin.internal.DaikinCommunicationException;
import org.openhab.binding.daikin.internal.DaikinDynamicStateDescriptionProvider;
import org.openhab.binding.daikin.internal.api.Enums.HomekitMode;
import org.openhab.binding.daikin.internal.api.SensorInfo;
import org.openhab.binding.daikin.internal.api.airbase.AirbaseControlInfo;
import org.openhab.binding.daikin.internal.api.airbase.AirbaseEnums.AirbaseFanSpeed;
import org.openhab.binding.daikin.internal.api.airbase.AirbaseEnums.AirbaseFeature;
import org.openhab.binding.daikin.internal.api.airbase.AirbaseEnums.AirbaseMode;
import org.openhab.binding.daikin.internal.api.airbase.AirbaseModelInfo;
import org.openhab.binding.daikin.internal.api.airbase.AirbaseZoneInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles communicating with a Daikin Airbase wifi adapter.
 *
 * @author Tim Waterhouse - Initial Contribution
 * @author Paul Smedley <paul@smedley.id.au> - Modifications to support Airbase Controllers
 * @author Jimmy Tanagra - Support Airside and auto fan levels, DynamicStateDescription
 *
 */
@NonNullByDefault
public class DaikinAirbaseUnitHandler extends DaikinBaseHandler {
    private final Logger logger = LoggerFactory.getLogger(DaikinAirbaseUnitHandler.class);
    private @Nullable AirbaseModelInfo airbaseModelInfo;

    public DaikinAirbaseUnitHandler(Thing thing, DaikinDynamicStateDescriptionProvider stateDescriptionProvider, @Nullable HttpClient httpClient) {
        super(thing, stateDescriptionProvider, httpClient);
    }

    @Override
    protected boolean handleCommandInternal(ChannelUID channelUID, Command command)
            throws DaikinCommunicationException {
        if (channelUID.getId().startsWith(DaikinBindingConstants.CHANNEL_AIRBASE_AC_ZONE)) {
            int zoneNumber = Integer.parseInt(channelUID.getId().substring(4));
            if (command instanceof OnOffType) {
                changeZone(zoneNumber, command == OnOffType.ON);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void pollStatus() throws IOException {
        AirbaseControlInfo controlInfo = webTargets.getAirbaseControlInfo();
        updateStatus(ThingStatus.ONLINE);

        if (airbaseModelInfo == null || !"OK".equals(airbaseModelInfo.ret)) {
            airbaseModelInfo = webTargets.getAirbaseModelInfo();
            updateChannelStateDescriptions();
        }

        if (controlInfo != null) {
            updateState(DaikinBindingConstants.CHANNEL_AC_POWER, controlInfo.power ? OnOffType.ON : OnOffType.OFF);
            updateTemperatureChannel(DaikinBindingConstants.CHANNEL_AC_TEMP, controlInfo.temp);
            updateState(DaikinBindingConstants.CHANNEL_AC_MODE, new StringType(controlInfo.mode.name()));
            updateState(DaikinBindingConstants.CHANNEL_AIRBASE_AC_FAN_SPEED,
                    new StringType(controlInfo.fanSpeed.name()));

            if (!controlInfo.power) {
                updateState(DaikinBindingConstants.CHANNEL_AC_HOMEKITMODE, new StringType(HomekitMode.OFF.getValue()));
            } else if (controlInfo.mode == AirbaseMode.COLD) {
                updateState(DaikinBindingConstants.CHANNEL_AC_HOMEKITMODE, new StringType(HomekitMode.COOL.getValue()));
            } else if (controlInfo.mode == AirbaseMode.HEAT) {
                updateState(DaikinBindingConstants.CHANNEL_AC_HOMEKITMODE, new StringType(HomekitMode.HEAT.getValue()));
            } else if (controlInfo.mode == AirbaseMode.AUTO) {
                updateState(DaikinBindingConstants.CHANNEL_AC_HOMEKITMODE, new StringType(HomekitMode.AUTO.getValue()));
            }
        }

        SensorInfo sensorInfo = webTargets.getAirbaseSensorInfo();
        if (sensorInfo != null) {
            updateTemperatureChannel(DaikinBindingConstants.CHANNEL_INDOOR_TEMP, sensorInfo.indoortemp);

            updateTemperatureChannel(DaikinBindingConstants.CHANNEL_OUTDOOR_TEMP, sensorInfo.outdoortemp);
        }

        AirbaseZoneInfo zoneInfo = webTargets.getAirbaseZoneInfo();
        if (zoneInfo != null) {
            IntStream.range(0, zoneInfo.zone.length)
                    .forEach(idx -> updateState(DaikinBindingConstants.CHANNEL_AIRBASE_AC_ZONE + idx,
                            OnOffType.from(zoneInfo.zone[idx])));
        }
    }

    @Override
    protected void changePower(boolean power) throws DaikinCommunicationException {
        AirbaseControlInfo info = webTargets.getAirbaseControlInfo();
        info.power = power;
        webTargets.setAirbaseControlInfo(info);
    }

    @Override
    protected void changeSetPoint(double newTemperature) throws DaikinCommunicationException {
        AirbaseControlInfo info = webTargets.getAirbaseControlInfo();
        info.temp = Optional.of(newTemperature);
        webTargets.setAirbaseControlInfo(info);
    }

    @Override
    protected void changeMode(String mode) throws DaikinCommunicationException {
        AirbaseMode newMode = AirbaseMode.valueOf(mode);
        if (airbaseModelInfo != null) {
            if ((newMode == AirbaseMode.AUTO && !airbaseModelInfo.features.contains(AirbaseFeature.AUTO))
                    || (newMode == AirbaseMode.DRY && !airbaseModelInfo.features.contains(AirbaseFeature.DRY))) {
                logger.warn("{} mode is not supported by your controller", mode);
                return;
            }
        }
        AirbaseControlInfo info = webTargets.getAirbaseControlInfo();
        info.mode = newMode;
        webTargets.setAirbaseControlInfo(info);
    }

    @Override
    protected void changeFanSpeed(String speed) throws DaikinCommunicationException {
        AirbaseFanSpeed newFanSpeed = AirbaseFanSpeed.valueOf(speed);
        if (airbaseModelInfo != null) {
            if (EnumSet.range(AirbaseFanSpeed.AUTO_LEVEL_1, AirbaseFanSpeed.AUTO_LEVEL_5).contains(newFanSpeed)
                    && !airbaseModelInfo.features.contains(AirbaseFeature.FRATE_AUTO)) {
                logger.warn("Fan AUTO_LEVEL_X is not supported by your controller");
                return;
            }
            if (newFanSpeed == AirbaseFanSpeed.AIRSIDE && !airbaseModelInfo.features.contains(AirbaseFeature.AIRSIDE)) {
                logger.warn("Airside is not supported by your controller");
                return;
            }
        }
        AirbaseControlInfo info = webTargets.getAirbaseControlInfo();
        info.fanSpeed = newFanSpeed;
        webTargets.setAirbaseControlInfo(info);
    }

    protected void changeZone(int zone, boolean command) throws DaikinCommunicationException {
        if (zone <= 0 || zone > airbaseModelInfo.zonespresent) {
            logger.warn("The given zone number ({}) is outside the number of zones supported by the controller ({})",
                    zone, airbaseModelInfo.zonespresent);
            return;
        }

        AirbaseZoneInfo zoneInfo = webTargets.getAirbaseZoneInfo();
        long count = IntStream.range(0, zoneInfo.zone.length).filter(idx -> zoneInfo.zone[idx]).count()
                + airbaseModelInfo.commonzone;
        logger.debug("Number of open zones: \"{}\"", count);

        if (count >= 1) {
            zoneInfo.zone[zone] = command;
            webTargets.setAirbaseZoneInfo(zoneInfo);
        }
    }

    @Override
    protected void registerUuid(@Nullable String key) {
        // not implemented. There is currently no known Airbase adapter that requires uuid authentication
    }

    protected void updateChannelStateDescriptions() {
        updateAirbaseFanSpeedChannelStateDescription();
        updateAirbaseModeChannelStateDescription();
    }

    protected void updateAirbaseFanSpeedChannelStateDescription() {
        List<StateOption> options = new ArrayList<>();
        options.add(new StateOption(AirbaseFanSpeed.AUTO.name(), AirbaseFanSpeed.AUTO.getLabel()));
        if (airbaseModelInfo.features.contains(AirbaseFeature.AIRSIDE)) {
            options.add(new StateOption(AirbaseFanSpeed.AIRSIDE.name(), AirbaseFanSpeed.AIRSIDE.getLabel()));
        }
        for (AirbaseFanSpeed f : EnumSet.range(AirbaseFanSpeed.LEVEL_1, AirbaseFanSpeed.LEVEL_5)) {
            options.add(new StateOption(f.name(), f.getLabel()));
        }
        if (airbaseModelInfo.features.contains(AirbaseFeature.FRATE_AUTO)) {
            for (AirbaseFanSpeed f : EnumSet.range(AirbaseFanSpeed.AUTO_LEVEL_1, AirbaseFanSpeed.AUTO_LEVEL_5)) {
                options.add(new StateOption(f.name(), f.getLabel()));
            }
        }
        stateDescriptionProvider.setStateOptions(
                new ChannelUID(thing.getUID(), DaikinBindingConstants.CHANNEL_AIRBASE_AC_FAN_SPEED), options);
    }

    protected void updateAirbaseModeChannelStateDescription() {
        List<StateOption> options = new ArrayList<>();
        if (airbaseModelInfo.features.contains(AirbaseFeature.AUTO)) {
            options.add(new StateOption(AirbaseMode.AUTO.name(), AirbaseMode.AUTO.getLabel()));
        }
        for (AirbaseMode f : EnumSet.complementOf(EnumSet.of(AirbaseMode.AUTO, AirbaseMode.DRY))) {
            options.add(new StateOption(f.name(), f.getLabel()));
        }
        if (airbaseModelInfo.features.contains(AirbaseFeature.DRY)) {
            options.add(new StateOption(AirbaseMode.DRY.name(), AirbaseMode.DRY.getLabel()));
        }
        stateDescriptionProvider.setStateOptions(new ChannelUID(thing.getUID(), DaikinBindingConstants.CHANNEL_AC_MODE),
                options);
    }
}
