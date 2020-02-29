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

import java.util.Locale;

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

    private final Bundle bundle;
    private final @Nullable TranslationProvider i18nProvider;
    private final @Nullable LocaleProvider localeProvider;

    public ShellyTranslationProvider(Bundle bundle, @Nullable TranslationProvider i18nProvider,
            @Nullable LocaleProvider localeProvider) {
        this.bundle = bundle;
        this.i18nProvider = i18nProvider;
        this.localeProvider = localeProvider;
    }

    public @Nullable String getText(String key, @Nullable Object... arguments) {
        Locale locale = localeProvider != null ? localeProvider.getLocale() : Locale.ENGLISH;
        return i18nProvider != null ? i18nProvider.getText(bundle, key, getDefaultText(key), locale, arguments) : key;
    }

    public @Nullable String getDefaultText(String key) {
        return i18nProvider != null ? i18nProvider.getText(bundle, key, key, Locale.ENGLISH) : key;
    }

}