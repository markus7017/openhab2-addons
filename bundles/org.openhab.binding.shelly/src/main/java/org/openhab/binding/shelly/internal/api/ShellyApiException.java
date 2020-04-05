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

    private ShellyApiResult apiResult = new ShellyApiResult();
    private static String EX_NONE = "none";
    private Exception e = new Exception(EX_NONE);

    public ShellyApiException(Exception exception) {
        super(exception);
        e = exception;
    }

    public ShellyApiException(String message) {
        super(message);
    }

    public ShellyApiException(ShellyApiResult res) {
        super(EX_NONE);
        apiResult = res;
    }

    public ShellyApiException(Exception exception, String message) {
        super(message, exception);
        e = exception;
    }

    public ShellyApiException(ShellyApiResult result, Exception exception) {
        super(exception);
        apiResult = result;
        e = exception;
    }

    @Override
    public String getMessage() {
        return isEmpty() ? "" : nonNullString(super.getMessage());
    }

    @Override
    public String toString() {
        String message = super.getMessage();
        String url = !apiResult.url.isEmpty() ? MessageFormat.format("{0} {1} HTTP {2} {3}", apiResult.method,
                apiResult.url, apiResult.httpCode, apiResult.httpReason) : "";
        String resultString = !apiResult.response.isEmpty() ? ", result =" + apiResult.response : "";

        if (!isEmpty()) {
            if (isUnknownHost()) {
                String[] string = message.split(": "); // java.net.UnknownHostException: api.rach.io
                return MessageFormat.format("Unable to connect to {0} (Unknown host / Network down / Low signal)",
                        string[1]);
            } else if (isMalformedURL()) {
                return MessageFormat.format("Invalid URL: {0}", url);
            } else if (isTimeout()) {
                return MessageFormat.format("Device unreachable or API Timeout ({0})", url);
            } else {
                return MessageFormat.format("{0} ({1})", e.toString(), message);
            }
        } else {
            if (isApiException()) {
                message = nonNullString(super.getClass().toString()) + " - " + getMessage();
            } else {
                message = getMessage();
            }
        }

        return MessageFormat.format("{0} {1} {2}", message, url, resultString);
    }

    public boolean isApiException() {
        return e.getClass() == ShellyApiException.class;
    }

    public boolean isTimeout() {
        Class<?> extype = !isEmpty() ? e.getClass() : null;
        return (apiResult.httpCode == -1)
                || (extype != null) && ((extype == TimeoutException.class) || (extype == ExecutionException.class)
                        || (extype == InterruptedException.class) || getMessage().toLowerCase().contains("timeout"));
    }

    public boolean isHttpAccessUnauthorized() {
        return apiResult.isHttpAccessUnauthorized();
    }

    public boolean isUnknownHost() {
        return e.getClass() == MalformedURLException.class;
    }

    public boolean isMalformedURL() {
        return e.getClass() == UnknownHostException.class;
    }

    public ShellyApiResult getApiResult() {
        return apiResult;
    }

    private boolean isEmpty() {
        return nonNullString(e.getMessage()).equals(EX_NONE);
    }

    private static String nonNullString(@Nullable String s) {
        return s != null ? s : "";
    }
}