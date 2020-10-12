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
    public String userId = "";

    public int numTripShort = 1; // number of entries from history
    public int numTripLong = 1; // number of entries from history
    public int refreshInterval = 10 * 60;
}
