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
package org.openhab.binding.shelly.internal.discovery;

import static org.eclipse.smarthome.core.thing.Thing.PROPERTY_MODEL_ID;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.getString;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.jmdns.ServiceInfo;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.eclipse.smarthome.core.i18n.LocaleProvider;
import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.io.net.http.HttpClientFactory;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsDevice;
import org.openhab.binding.shelly.internal.api.ShellyApiResult;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyThingConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyBaseHandler;
import org.openhab.binding.shelly.internal.util.ShellyTranslationProvider;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class identifies Shelly devices by their mDNS service information.
 *
 * @author Hans-Jörg Merk - Initial contribution
 */
@NonNullByDefault
@Component(service = MDNSDiscoveryParticipant.class, immediate = true)
public class ShellyDiscoveryParticipant implements MDNSDiscoveryParticipant {
    private final Logger logger = LoggerFactory.getLogger(ShellyDiscoveryParticipant.class);
    private final ShellyBindingConfiguration bindingConfig = new ShellyBindingConfiguration();
    private final ShellyTranslationProvider messages = new ShellyTranslationProvider();
    private final HttpClient httpClient;

    /**
     * OSGI Service Activation
     *
     * @param componentContext
     * @param localeProvider
     */
    @Activate
    public ShellyDiscoveryParticipant(@Reference HttpClientFactory httpClientFactory,
            @Reference LocaleProvider localeProvider, @Reference TranslationProvider i18nProvider,
            ComponentContext componentContext) {
        logger.debug("Activating ShellyDiscovery service");
        this.messages.initFrom(new ShellyTranslationProvider(componentContext.getBundleContext().getBundle(),
                i18nProvider, localeProvider));
        this.httpClient = httpClientFactory.getCommonHttpClient();
        bindingConfig.updateFromProperties(componentContext.getProperties());
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return SUPPORTED_THING_TYPES_UIDS;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    /**
     * Process updates to Binding Config
     *
     * @param componentContext
     */
    @Modified
    protected void modified(final ComponentContext componentContext) {
        logger.debug("Shelly Binding Configuration refreshed");
        bindingConfig.updateFromProperties(componentContext.getProperties());
    }

    @Nullable
    @Override
    public DiscoveryResult createResult(final ServiceInfo service) {
        String name = service.getName().toLowerCase(); // Duao: Name starts with" Shelly" rather than "shelly"
        if (!name.startsWith("shelly")) {
            return null;
        }

        String address = "";
        try {
            String mode = "";
            String model = "unknown";
            ThingUID thingUID = null;
            ShellyDeviceProfile profile = null;
            Map<String, Object> properties = new TreeMap<>();

            name = service.getName().toLowerCase();
            String thingType = name.contains("-") ? StringUtils.substringBefore(name, "-") : name;
            ;

            address = StringUtils.substringBetween(service.toString(), "/", ":80");
            if (address == null) {
                logger.debug("Shelly device {} discovered with empty IP address", name);
                return null;
            }
            logger.debug("Shelly device discovered: IP-Adress={}, name={}", address, name);

            // Get device settings
            ShellyThingConfiguration config = new ShellyThingConfiguration();
            config.deviceIp = address;
            config.userId = bindingConfig.defaultUserId;
            config.password = bindingConfig.defaultPassword;

            try {
                ShellyHttpApi api = new ShellyHttpApi(name, config, httpClient);
                ShellySettingsDevice devInfo = api.getDevInfo();

                profile = api.getDeviceProfile(thingType);
                logger.debug("{}: Shelly settings : {}", getString(devInfo.hostname), profile.settingsJson);
                model = getString(profile.settings.device.type);
                mode = profile.mode;

                properties = ShellyBaseHandler.fillDeviceProperties(profile);
                logger.trace("{}: thingType={}, deviceType={}, mode={}", name, thingType, profile.deviceType,
                        mode.isEmpty() ? "<standard>" : mode);

                // get thing type from device name
                thingUID = ShellyDeviceProfile.getThingUID(name, mode, false);
            } catch (ShellyApiException e) {
                ShellyApiResult result = e.getApiResult();
                if (result.isHttpAccessUnauthorized()) {
                    logger.info("{}: {}", name, messages.get("discovery.protected", address));

                    // create shellyunknown thing - will be changed during thing initialization with valid credentials
                    thingUID = ShellyDeviceProfile.getThingUID(name, mode, true);
                } else {
                    logger.info("{}: {}", name, messages.get("discovery.failed", address, e.toString()));
                    logger.debug("{}: Exception {}\n{}", name, e.toString(), e.getStackTrace());
                }
            } catch (IllegalArgumentException | NullPointerException e) { // maybe some format description was buggy
                logger.debug("{}: Unable to initialize - {} ({}), retrying later\n{}", name, getString(e),
                        getString(e.getClass().toString()), e.getStackTrace());
            }

            if (thingUID != null) {
                addProperty(properties, CONFIG_DEVICEIP, address);
                addProperty(properties, PROPERTY_MODEL_ID, model);
                addProperty(properties, PROPERTY_SERVICE_NAME, name);
                addProperty(properties, PROPERTY_DEV_TYPE, thingType);
                addProperty(properties, PROPERTY_DEV_MODE, mode);

                logger.debug("{}: Adding Shelly thing, UID={}", name, thingUID.getAsString());
                return DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                        .withLabel(name + " - " + address).withRepresentationProperty(name).build();
            }
        } catch (IllegalArgumentException | NullPointerException e) { // maybe some format description was buggy
            logger.info("{}", messages.get("discovery.failed", name, address, getString(e)));
            logger.debug("{}: Exception {}\n{}", name, e.getClass(), e.getStackTrace());
        }
        return null;
    }

    private void addProperty(Map<String, Object> properties, String key, @Nullable String value) {
        properties.put(key, value != null ? value : "");
    }

    @Nullable
    @Override
    public ThingUID getThingUID(@Nullable ServiceInfo service) {
        logger.debug("ServiceInfo {}", service);
        Validate.notNull(service);
        return ShellyDeviceProfile.getThingUID(service.getName().toLowerCase(), "", false);
    }
}
