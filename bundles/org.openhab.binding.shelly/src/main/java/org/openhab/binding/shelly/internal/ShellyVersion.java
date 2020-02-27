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
package org.openhab.binding.shelly.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link ShellyVersion} compares 2 version strings.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVersion implements Comparable<ShellyVersion> {

    private String version;

    public final String get() {
        return this.version;
    }

    public ShellyVersion(@Nullable String version) {
        if (version == null) {
            throw new IllegalArgumentException("Version can not be null");
        }
        String fwvers = "";
        if (version.contains("v") && version.contains(".")) {
            String s = version.replaceAll("v", "");
            fwvers = s.replaceAll("\\.", "");
        }
        if (!fwvers.matches("[0-9]+(\\.[0-9]+)*")) {
            throw new IllegalArgumentException("Invalid version format");
        }
        this.version = fwvers;
    }

    @Override
    public int compareTo(@Nullable ShellyVersion that) {
        if (that == null) {
            return 1;
        }
        String[] thisParts = this.get().split("\\.");
        String[] thatParts = that.get().split("\\.");
        int length = Math.max(thisParts.length, thatParts.length);
        for (int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length ? Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length ? Integer.parseInt(thatParts[i]) : 0;
            if (thisPart < thatPart) {
                return -1;
            }
            if (thisPart > thatPart) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(@Nullable Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (this.getClass() != that.getClass()) {
            return false;
        }
        return this.compareTo((ShellyVersion) that) == 0;
    }

    @SuppressWarnings("null")
    public boolean checkBeta(@Nullable String version) {
        return version != null & !version.toLowerCase().contains("master");
    }

}
