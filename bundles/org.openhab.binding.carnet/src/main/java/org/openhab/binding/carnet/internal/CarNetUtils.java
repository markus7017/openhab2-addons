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
package org.openhab.binding.carnet.internal;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import javax.measure.Unit;
import javax.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;

/**
 * Helperfunctions
 *
 * @author Markus Michels - Initial Contribution
 *
 */
@NonNullByDefault
public class CarNetUtils {
    public static String getString(@Nullable String value) {
        return value != null ? value : "";
    }

    public static String substringBefore(@Nullable String string, String pattern) {
        if (string != null) {
            int pos = string.indexOf(pattern);
            if (pos > 0) {
                return string.substring(0, pos);
            }
        }
        return "";
    }

    public static String substringBeforeLast(@Nullable String string, String pattern) {
        if (string != null) {
            int pos = string.lastIndexOf(pattern);
            if (pos > 0) {
                return string.substring(0, pos);
            }
        }
        return "";
    }

    public static String substringAfter(@Nullable String string, String pattern) {
        if (string != null) {
            int pos = string.indexOf(pattern);
            if (pos != -1) {
                return string.substring(pos + pattern.length());
            }
        }
        return "";
    }

    public static String substringAfterLast(@Nullable String string, String pattern) {
        if (string != null) {
            int pos = string.lastIndexOf(pattern);
            if (pos != -1) {
                return string.substring(pos + pattern.length());
            }
        }
        return "";
    }

    public static String substringBetween(@Nullable String string, String begin, String end) {
        if (string != null) {
            int s = string.indexOf(begin);
            if (s != -1) {
                // The end tag might be included before the start tag, e.g.
                // when using "http://" and ":" to get the IP from http://192.168.1.1:8081/xxx
                // therefore make it 2 steps
                String result = string.substring(s + begin.length());
                return substringBefore(result, end);
            }
        }
        return "";
    }

    public static String getMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : "";
    }

    public static Integer getInteger(@Nullable Integer value) {
        return (value != null ? (Integer) value : 0);
    }

    public static Long getLong(@Nullable Long value) {
        return (value != null ? (Long) value : 0);
    }

    public static Double getDouble(@Nullable Double value) {
        return (value != null ? (Double) value : 0);
    }

    public static Boolean getBool(@Nullable Boolean value) {
        return (value != null ? (Boolean) value : false);
    }

    // as State

    public static StringType getStringType(@Nullable String value) {
        return new StringType(value != null ? value : "");
    }

    public static DecimalType getDecimal(@Nullable Double value) {
        return new DecimalType((value != null ? value : 0));
    }

    public static DecimalType getDecimal(@Nullable Integer value) {
        return new DecimalType((value != null ? value : 0));
    }

    public static DecimalType getDecimal(@Nullable Long value) {
        return new DecimalType((value != null ? value : 0));
    }

    public static OnOffType getOnOff(@Nullable Boolean value) {
        return (value != null ? value ? OnOffType.ON : OnOffType.OFF : OnOffType.OFF);
    }

    public static OnOffType getOnOff(int value) {
        return value == 0 ? OnOffType.OFF : OnOffType.ON;
    }

    public static State toQuantityType(@Nullable Double value, int digits, Unit<?> unit) {
        if (value == null) {
            return UnDefType.NULL;
        }
        BigDecimal bd = new BigDecimal(value.doubleValue());
        if (digits >= 1) {
            bd = bd.setScale(digits, BigDecimal.ROUND_HALF_UP);
        }
        return toQuantityType(bd, unit);
    }

    public static State toQuantityType(@Nullable Number value, Unit<?> unit) {
        return value == null ? UnDefType.NULL : new QuantityType<>(value, unit);
    }

    public static State toQuantityType(@Nullable PercentType value, Unit<?> unit) {
        return value == null ? UnDefType.NULL : toQuantityType(value.toBigDecimal(), unit);
    }

    public static Long now() {
        return System.currentTimeMillis() / 1000L;
    }

    public static DateTimeType getTimestamp() {
        return new DateTimeType(ZonedDateTime.ofInstant(Instant.ofEpochSecond(now()), ZoneId.systemDefault()));
    }

    public static DateTimeType getTimestamp(String zone, long timestamp) {
        try {
            if (timestamp == 0) {
                return getTimestamp();
            }
            ZoneId zoneId = !zone.isEmpty() ? ZoneId.of(zone) : ZoneId.systemDefault();
            ZonedDateTime zdt = LocalDateTime.now().atZone(zoneId);
            int delta = zdt.getOffset().getTotalSeconds();
            return new DateTimeType(ZonedDateTime.ofInstant(Instant.ofEpochSecond(timestamp - delta), zoneId));
        } catch (DateTimeException e) {
            // Unable to convert device's timezone, use system one
            return getTimestamp();
        }
    }

    public static State getDateTime(String timestamp) {
        /*
         * ZonedDateTime gmtTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ssZ"))
         * .atZone(ZoneId.of("GMT"));
         * LocalDateTime localTime = gmtTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
         * ZonedDateTime zdt = localTime.atZone(ZoneId.systemDefault());
         * return new DateTimeType(zdt);
         *
         * return new DateTimeType(timestamp);
         */
        try {
            Date date = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(timestamp)));
            return new DateTimeType(ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
        } catch (DateTimeException e) {
            return UnDefType.UNDEF;
        }
    }

    public static String sha512(String pin, String challenge) throws CarNetException {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-512");
            byte[] pinBytes = DatatypeConverter.parseHexBinary(pin);
            byte[] challengeBytes = DatatypeConverter.parseHexBinary(challenge);
            ByteBuffer input = ByteBuffer.allocate(pinBytes.length + challengeBytes.length);
            input.put(pinBytes);
            input.put(challengeBytes);
            byte[] digest = hash.digest(input.array());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; ++i) {
                sb.append(Integer.toHexString((digest[i] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new CarNetException("sha512() failed", e);
        }
    }

    public static String mkChannelId(String group, String channel) {
        return group + "#" + channel;
    }
}
