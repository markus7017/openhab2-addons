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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;

/**
 * The {@link ShellyApiResult} wraps up the API result and provides some more information like url, http code, received
 * response etc.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyApiResult {
    public String url = "";
    public String method = "";
    public String response = "";
    public Integer httpCode = 0;
    public String httpReason = "";

    public ShellyApiResult() {

    }

    public ShellyApiResult(String url, String method) {
        this.method = method;
        this.url = url;
    }

    public ShellyApiResult(String url, String method, Integer responseCode, String response) {
        this.method = method;
        this.url = url;
        this.httpCode = 0;
        this.response = response;
    }

    @SuppressWarnings("null")
    public ShellyApiResult(@Nullable ContentResponse contentResponse) {
        fillFromResponse(contentResponse);
    }

    public ShellyApiResult(@Nullable ContentResponse contentResponse, Throwable e) {
        fillFromResponse(contentResponse);
        response = response + "(" + e.toString() + ")";
    }

    public ShellyApiResult(@Nullable Request request, Throwable e) {
        response = e.toString();
        if (request != null) {
            url = request.getURI().toString();
            method = request.getMethod();
        }
    }

    private void fillFromResponse(@Nullable ContentResponse contentResponse) {
        if (contentResponse != null) {
            String r = contentResponse.getContentAsString();
            response = r != null ? r : "";
            httpCode = contentResponse.getStatus();
            httpReason = contentResponse.getReason();

            Request request = contentResponse.getRequest();
            if (request != null) {
                url = request.getURI().toString();
                method = request.getMethod();
            }
        }
    }
}
