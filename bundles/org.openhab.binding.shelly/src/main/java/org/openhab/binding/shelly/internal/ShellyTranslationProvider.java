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

import org.eclipse.smarthome.core.i18n.LocaleProvider;
import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.osgi.framework.Bundle;

/**
 * {@link ShellyTranslationProvider} provides i18n message lookup
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyTranslationProvider {

    final private Bundle bundle;
    final private TranslationProvider i18nProvider;
    final private LocaleProvider localeProvider;

    public ShellyTranslationProvider(Bundle bundle, TranslationProvider i18nProvider, LocaleProvider localeProvider) {
        this.bundle = bundle;
        this.i18nProvider = i18nProvider;
        this.localeProvider = localeProvider;
    }

    public String getText(String key, Object... arguments) {
        Locale locale = localeProvider != null ? localeProvider.getLocale() : Locale.ENGLISH;
        return i18nProvider != null ? i18nProvider.getText(bundle, key, getDefaultText(key), locale, arguments) : key;
    }

    public String getDefaultText(String key) {
        return i18nProvider.getText(bundle, key, key, Locale.ENGLISH);
    }

}