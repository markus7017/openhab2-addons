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

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.i18n.LocaleProvider;
import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link CarNetHandlerFactory} retrieves localized text resources as defined in properties file.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@Component(service = CarNetTextResources.class)
public class CarNetTextResources {
    private final Bundle bundle;
    private final TranslationProvider i18nProvider;
    private final LocaleProvider localeProvider;

    @Activate
    public CarNetTextResources(@Reference TranslationProvider i18nProvider, @Reference LocaleProvider localeProvider) {
        this.bundle = FrameworkUtil.getBundle(this.getClass());
        this.i18nProvider = i18nProvider;
        this.localeProvider = localeProvider;
    }

    public String get(String key, @Nullable Object... arguments) {
        return getText(key.contains("@text/") ? key : "message." + key, arguments);
    }

    public String getText(String key, @Nullable Object... arguments) {
        try {
            Locale locale = localeProvider.getLocale();
            String message = i18nProvider.getText(bundle, key, getDefaultText(key), locale, arguments);
            if (message != null) {
                return message;
            }
        } catch (IllegalArgumentException e) {
        }
        return "Unable to load message for key " + key;
    }

    public @Nullable String getDefaultText(String key) {
        return i18nProvider.getText(bundle, key, key, Locale.ENGLISH);
    }
}
