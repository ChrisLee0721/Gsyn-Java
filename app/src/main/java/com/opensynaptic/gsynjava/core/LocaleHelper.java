package com.opensynaptic.gsynjava.core;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Per-app language switching via AppCompatDelegate (AppCompat 1.6+).
 * No manual attachBaseContext override is required — AppCompat handles
 * locale persistence and activity recreation automatically.
 */
public class LocaleHelper {

    public static final String LANG_SYSTEM = "system";
    public static final String LANG_EN = "en";
    public static final String LANG_ZH = "zh";

    /**
     * Apply and persist the chosen language. The current activity will be
     * recreated automatically by AppCompat.
     */
    public static void applyAndSave(String lang) {
        LocaleListCompat locales;
        if (LANG_SYSTEM.equals(lang)) {
            locales = LocaleListCompat.getEmptyLocaleList();
        } else {
            locales = LocaleListCompat.forLanguageTags(lang);
        }
        AppCompatDelegate.setApplicationLocales(locales);
    }

    /**
     * Return the currently active language tag (one of LANG_* constants).
     */
    public static String current() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) return LANG_SYSTEM;
        String tag = locales.get(0).getLanguage(); // "en" or "zh"
        if (LANG_ZH.equals(tag)) return LANG_ZH;
        if (LANG_EN.equals(tag)) return LANG_EN;
        return LANG_SYSTEM;
    }
}
