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
package org.openhab.binding.shelly.internal.api;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link CarNetException} implements an extension to the standard Exception class. This allows to keep also the
 * result of the last API call (e.g. including the http status code in the message).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyApiException extends Exception {
    private static final long serialVersionUID = -5809459454769761821L;

    private @Nullable Exception e = null;
    private ShellyApiResult apiResult = new ShellyApiResult();

    public ShellyApiException() {
        super();
    }

    public ShellyApiException(String message) {
        super(message);
    }

    public ShellyApiException(Exception exception) {
        super(exception);
        e = exception;
    }

    public ShellyApiException(Exception exception, String message) {
        super(message, exception);
        e = exception;
    }

    public ShellyApiException(ShellyApiResult result) {
        apiResult = result;
    }

    public ShellyApiException(ShellyApiResult result, Exception exception) {
        super(exception);
        apiResult = result;
        e = exception;
    }

    @Override
    public String getMessage() {
        return getString(super.getMessage());
    }

    @SuppressWarnings("null")
    @Override
    public String toString() {
        String message = super.getMessage();
        String url = !apiResult.url.isEmpty() ? MessageFormat.format("{0} {1} HTTP {2} {3}", apiResult.method,
                apiResult.url, apiResult.httpCode, apiResult.httpReason) : "";
        String resultString = !apiResult.response.isEmpty()
                ? MessageFormat.format(", result = '{0}'", apiResult.response)
                : "";

        if (e != null) {
            if (isUnknownHost()) {
                String[] string = message.split(": "); // java.net.UnknownHostException: api.rach.io
                return MessageFormat.format("Unable to connect to {0} (Unknown host / Network down / Low signal)",
                        string[1]);
            } else if (isMalformedURL()) {
                return MessageFormat.format("Invalid URL: {0}", url);
            } else if (isTimeout()) {
                return MessageFormat.format("API Timeout or device unreachable ({0})", url);
            } else {
                return MessageFormat.format("{0} ({1})", e.toString(), message);
            }
        } else {
            if (isApiException()) {
                message = MessageFormat.format("{0} {1}", getString(super.getClass().toString()),
                        getString(super.getMessage()));
            } else {
                message = getString(super.getMessage());
            }
        }

        return MessageFormat.format("{0} {1} {2}", message, url, resultString);
    }

    public boolean isApiException() {
        return (e != null) && (e.getClass() == ShellyApiException.class);
    }

    public boolean isTimeout() {
        Class<?> extype = e != null ? e.getClass() : null;
        return (apiResult.httpCode == -1) || (e != null) && (extype != null)
                && ((extype == TimeoutException.class) || (extype == ExecutionException.class)
                        || (extype == InterruptedException.class) || getMessage().toLowerCase().contains("timeout"));
    }

    @SuppressWarnings("null")
    public boolean isHttpAccessUnauthorized() {
        return apiResult != null ? apiResult.isHttpAccessUnauthorized() : false;
    }

    public boolean isUnknownHost() {
        return (e != null) && (e.getClass() == MalformedURLException.class);
    }

    public boolean isMalformedURL() {
        return (e != null) && (e.getClass() == UnknownHostException.class);
    }

    @SuppressWarnings("null")
    public ShellyApiResult getApiResult() {
        return apiResult != null ? apiResult : new ShellyApiResult();
    }

    public static String getExceptionType(@Nullable ShellyApiException e) {
        if ((e == null) || e.getClass().toString().isEmpty()) {
            return "";
        }

        String msg = StringUtils.substringAfterLast(e.getClass().toString(), ".");
        if (msg != null) {
            return msg;
        }
        return e.getCause().toString();
    }

    private static String getString(@Nullable String s) {
        return s != null ? s : "";
    }
}