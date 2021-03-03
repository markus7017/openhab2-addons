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

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.text.MessageFormat;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.carnet.internal.api.CarNetApiResult;

/**
 * The {@link CarNetException} implements an extension to the standard Exception class. This allows to keep also the
 * result of the last API call (e.g. including the http status code in the message).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetException extends Exception {
    private static final long serialVersionUID = -5809459454769761821L;

    private @Nullable Throwable e = null;
    private CarNetApiResult apiResult = new CarNetApiResult();

    public CarNetException(String message) {
        super(message);
    }

    public CarNetException(String message, Throwable throwable) {
        super(message, throwable);
        e = throwable;
    }

    public CarNetException(String message, CarNetApiResult result) {
        super(message);
        apiResult = result;
    }

    public CarNetException(String message, CarNetApiResult result, Throwable throwable) {
        super(message);
        apiResult = result;
        e = throwable;
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }

    @SuppressWarnings({ "null", "unused" })
    @Override
    public String toString() {
        String message = super.getMessage();
        if (e != null) {
            if (e.getClass() == UnknownHostException.class) {
                String[] string = message.split(": "); // java.net.UnknownHostException: api.rach.io
                message = MessageFormat.format("Unable to connect to {0} (unknown host / internet connection down)",
                        string[1]);
            } else if (e.getClass() == MalformedURLException.class) {
                message = MessageFormat.format("Invalid URL: '{0}'", message);
            } else {
                message = MessageFormat.format("'{0}' ({1}", e.toString(), e.getMessage());
            }
        } else {
            if (super.getClass() != CarNetException.class) {
                message = MessageFormat.format("{0} {1}", super.getClass().toString(), super.getMessage());
            } else {
                message = super.getMessage();
            }
        }

        String url = !apiResult.url.isEmpty() ? MessageFormat.format("{0} {1} (HTTP {2} {3})", apiResult.method,
                apiResult.url, apiResult.httpCode, apiResult.httpReason) : "";
        String resultString = !apiResult.response.isEmpty() ? MessageFormat.format(", result = {0}", apiResult.response)
                : "";
        return MessageFormat.format("{0} {1}{2}", message, url, resultString);
    }

    public CarNetApiResult getApiResult() {
        return apiResult;
    }
}
