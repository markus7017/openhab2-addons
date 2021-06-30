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
package org.openhab.binding.carnet.internal.api.weconnect;

import java.util.ArrayList;

/**
 * {@link WeConnectApiJsonDTO} defines the We Connect API data formats
 *
 * @author Markus Michels - Initial contribution
 */
public class WeConnectApiJsonDTO {
    public static class WCVehicleList {
        public static class WCVehicle {
            /*
             * "vin": "WVWZZZE1ZMP053898",
             * "role": "PRIMARY_USER",
             * "enrollmentStatus": "COMPLETED",
             * "model": "ID.3",
             * "nickname": "ID.3",
             * "capabilities": []
             */

            public static class WCCapability {
                /*
                 * {
                 * "id": "automation",
                 * "expirationDate": "2024-05-09T00:00:00Z",
                 * "userDisablingAllowed": true
                 * }
                 */
                public String id;
                public String expirationDate;
                public Boolean userDisablingAllowed;
            }

            public String vin;
            public String role;
            public String enrollmentStatus;
            public String vehicle;
            public String model;
            public String nickname;
            public ArrayList<WCCapability> capabilities;
        }

        public ArrayList<WCVehicle> data;
    }

    public static class WCVehicleStatusData {
        public class WCVehicleStatus {
            public class WCBatteryStatus {
                /*
                 * "batteryStatus": {
                 * "carCapturedTimestamp": "2021-06-25T14:01:35Z",
                 * "currentSOC_pct": 52,
                 * "cruisingRangeElectric_km": 221
                 * },
                 */
                public String carCapturedTimestamp;
                public Integer currentSOC_pct;
                public Integer cruisingRangeElectric_km;
            }

            public class WCChargingStatus {
                /*
                 * "chargingStatus": {
                 * "carCapturedTimestamp": "2021-06-25T14:01:35Z",
                 * "remainingChargingTimeToComplete_min": 0,
                 * "chargingState": "readyForCharging",
                 * "chargeMode": "manual",
                 * "chargePower_kW": 0,
                 * "chargeRate_kmph": 0
                 * },
                 */
                public String carCapturedTimestamp;
                public Integer remainingChargingTimeToComplete_min;
                public String chargingState;
                public String chargeMode;
                public Integer chargePower_kW;
                public Integer chargeRate_kmph;
            }

            public class WCChargingSettings {
                /*
                 * "chargingSettings": {
                 * "carCapturedTimestamp": "2021-06-25T23:06:41Z",
                 * "maxChargeCurrentAC": "maximum",
                 * "autoUnlockPlugWhenCharged": "permanent",
                 * "targetSOC_pct": 90
                 * },
                 */
                public String carCapturedTimestamp;
                public String maxChargeCurrentAC;
                public String autoUnlockPlugWhenCharged;
                public Integer targetSOC_pct;
            }

            public class WCPlugStatus {
                /*
                 * "plugStatus": {
                 * "carCapturedTimestamp": "2021-06-25T23:06:41Z",
                 * "plugConnectionState": "disconnected",
                 * "plugLockState": "unlocked"
                 * },
                 */
                public String carCapturedTimestamp;
                public String plugConnectionState;
                public String plugLockState;
            }

            public class WCClimatisationStatus {
                /*
                 * "climatisationStatus": {
                 * "carCapturedTimestamp": "2021-06-25T23:06:40Z",
                 * "remainingClimatisationTime_min": 0,
                 * "climatisationState": "off"
                 * },
                 */
                public String carCapturedTimestamp;
                public Integer remainingClimatisationTime_min;
                public String climatisationState;
            }

            public class WCClimatisationSettings {
                /*
                 * "climatisationSettings": {
                 * "carCapturedTimestamp": "2021-06-25T23:06:47Z",
                 * "targetTemperature_K": 295.15,
                 * "targetTemperature_C": 22,
                 * "climatisationWithoutExternalPower": true,
                 * "climatizationAtUnlock": false,
                 * "windowHeatingEnabled": false,
                 * "zoneFrontLeftEnabled": false,
                 * "zoneFrontRightEnabled": false
                 * },
                 */
                public String carCapturedTimestamp;
                public Double targetTemperature_K;
                public Double targetTemperature_C;
                public Boolean climatisationWithoutExternalPower;
                public Boolean climatizationAtUnlock;
                public Boolean windowHeatingEnabled;
                public Boolean zoneFrontLeftEnabled;
                public Boolean zoneFrontRightEnabled;
            }

            public class WCClimatisationTimer {
                /*
                 * {
                 * "id": 1,
                 * "enabled": false,
                 * "singleTimer": {
                 * "startDateTime": "1999-12-31T22:00:00Z"
                 * }
                 */
                public class WCClimaTimer {
                    public class WCSingleTimer {
                        public String startDateTime;
                    }

                    public String id;
                    public Boolean enabled;
                }

                public ArrayList<WCClimaTimer> timers;
                public String carCapturedTimestamp;
                public String timeInCar;
            }

            public class WCWindowHeatingStatus {
                /*
                 * "windowHeatingStatus": {
                 * "carCapturedTimestamp": "2021-06-25T14:01:37Z",
                 * "windowHeatingStatus": [
                 * {
                 * "windowLocation": "front",
                 * "windowHeatingState": "off"
                 * },
                 * {
                 * "windowLocation": "rear",
                 * "windowHeatingState": "off"
                 * }
                 * ]
                 * },
                 */

                public class WCHeatingStatus {
                    public String windowLocation;
                    public String windowHeatingState;
                }

                public String carCapturedTimestamp;
                public ArrayList<WCHeatingStatus> windowHeatingStatus;
            }

            public class WCRangeStatus {
                /*
                 * "rangeStatus":
                 * {
                 * "carCapturedTimestamp": "2021-06-25T14:01:35Z",
                 * "carType": "electric",
                 * "primaryEngine": {
                 * "type": "electric",
                 * "currentSOC_pct": 52,
                 * "remainingRange_km": 221
                 * },
                 * "totalRange_km": 221
                 * },
                 */
                public class WCEngine {
                    public String type;
                    public Integer currentSOC_pct;
                    public Integer remainingRange_km;
                }

                public String carCapturedTimestamp;
                public String carType;
                public WCEngine primaryEngine;
                public Integer totalRange_km;
            }

            public class WCCapabilityStatus {
                /*
                 * "capabilityStatus":
                 * {
                 * "capabilities": [
                 * {
                 * "id": "automation",
                 * "expirationDate": "2024-05-09T00:00:00Z",
                 * "userDisablingAllowed": true
                 * },
                 */
                public class WCCapability {
                    public String id;
                    public String expirationDate;
                    public Boolean userDisablingAllowed;
                    public ArrayList<Integer> status;
                }

                public ArrayList<WCCapability> capabilities;
            }

            public WCBatteryStatus batteryStatus;
            public WCChargingStatus chargingStatus;
            public WCChargingSettings chargingSettings;
            public WCPlugStatus plugStatus;
            public WCClimatisationStatus climatisationStatus;
            public WCClimatisationSettings climatisationSettings;
            public WCClimatisationTimer climatisationTimer;
            public WCWindowHeatingStatus windowHeatingStatus;
            public WCRangeStatus rangeStatus;
            public WCCapabilityStatus capabilityStatus;
            public String error;
        }

        public WCVehicleStatus data;
    }
}
