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
package org.openhab.binding.shelly.internal.util;

import java.util.Locale;

import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.i18n.LocaleProvider;
import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.osgi.framework.Bundle;

/**
 * {@link ShellyTranslationProvider} provides i18n message lookup
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyTranslationProvider {

    private @Nullable Bundle bundle;
    private @Nullable TranslationProvider i18nProvider;
    private @Nullable LocaleProvider localeProvider;

    public ShellyTranslationProvider() {
    }

    public ShellyTranslationProvider(Bundle bundle, TranslationProvider i18nProvider, LocaleProvider localeProvider) {
        this.bundle = bundle;
        this.i18nProvider = i18nProvider;
        this.localeProvider = localeProvider;
    }

    public ShellyTranslationProvider(final ShellyTranslationProvider other) {
        initFrom(other);
    }

    public ShellyTranslationProvider initFrom(final ShellyTranslationProvider other) {
        this.bundle = other.bundle;
        this.i18nProvider = other.i18nProvider;
        this.localeProvider = other.localeProvider;
        return this;
    }

    public @Nullable String get(String key, @Nullable Object... arguments) {
        return getText(key.contains("@text/") || key.contains(".shelly.") ? key : "message." + key, arguments);
    }

    public @Nullable String getText(String key, @Nullable Object... arguments) {
        Validate.notNull(localeProvider, "ShellyTranslationProvider() not initialized");
        Locale locale = localeProvider != null ? localeProvider.getLocale() : Locale.ENGLISH;
        return i18nProvider != null ? i18nProvider.getText(bundle, key, getDefaultText(key), locale, arguments) : key;
    }

    public @Nullable String getDefaultText(String key) {
        Validate.notNull(i18nProvider, "ShellyTranslationProvider() not initialized");
        try {
            return i18nProvider != null ? i18nProvider.getText(bundle, key, key, Locale.ENGLISH) : key;
        } catch (NullPointerException e) {
            return "Unable to get message with key 'key'" + e.getMessage();
        }
    }

}