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
package org.openhab.binding.carnet.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link CarNetVehicleConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetVehicleConfiguration {
    public String vin = "";
    public String pin = "";
    public String homeRegionUrl = "";

    public static class CarNetUserInfo {
        public String id = "";
        public String oauthId = "";
        public String role = "";
        public String status = "";
        public String pairingCode = "";
        public String securityLevel = "";
    }

    public int numShortTrip = 1; // number of entries from short trip data history
    public int numLongTrip = 1; // number of entries from long trip data history
    public int numActionHistory = 1; // number of entries from action history
    public int numDestinations = 1; // number of entries from the destination history;

    public int refreshInterval = 10 * 60;
}
