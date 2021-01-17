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
package org.openhab.binding.shelly.internal.manager;

import static org.openhab.binding.shelly.internal.manager.ShellyManagerConstants.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyBaseHandler;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * {@link ShellyManager} implements the Shelly Manager
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyManager {
    private final Map<String, ShellyManagerPage> pages = new LinkedHashMap<>();
    private final ShellyBindingConfiguration bindingConfig = new ShellyBindingConfiguration();

    public ShellyManager(ConfigurationAdmin configurationAdmin, HttpClient httpClient,
            Map<String, ShellyBaseHandler> thingHandlers) {

        pages.put(SHELLY_MGR_OVERVIEW_URI,
                new ShellyManagerOverviewPage(configurationAdmin, httpClient, thingHandlers));
        pages.put(SHELLY_MGR_ACTION_URI, new ShellyManagerActionPage(configurationAdmin, httpClient, thingHandlers));
        pages.put(SHELLY_MGR_FWUPDATE_URI,
                new ShellyManagerFwUpdatePage(configurationAdmin, httpClient, thingHandlers));
        pages.put(SHELLY_MANAGER_URI, new ShellyManagerOverviewPage(configurationAdmin, httpClient, thingHandlers));
    }

    public String generateContent(String path, Map<String, String[]> parameters) throws ShellyApiException {
        for (Map.Entry<String, ShellyManagerPage> page : pages.entrySet()) {
            if (path.toLowerCase().startsWith(page.getKey())) {
                ShellyManagerPage p = page.getValue();
                return p.generateContent(parameters);
            }
        }
        return "Invalid URL or syntax";
    }
}