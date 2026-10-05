package com.trueproof.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Handles switching the WHOLE APP between English and Tamil at runtime,
 * and remembers the choice using SharedPreferences so it persists after
 * the app is closed and reopened.
 *
 * Usage:
 *   LocaleHelper.setLocale(context, "ta");   // switch to Tamil
 *   LocaleHelper.setLocale(context, "en");   // switch to English
 *
 * Call LocaleHelper.onAttach(context) inside attachBaseContext() of every
 * Activity (see MainActivity for the pattern) so the saved language is
 * applied every time an Activity is created.
 */
public class LocaleHelper {

    private static final String PREFS_NAME = "trueproof_prefs";
    private static final String KEY_LANGUAGE = "app_language";

    public static Context onAttach(Context context) {
        String lang = getSavedLanguage(context);
        return setLocale(context, lang);
    }

    public static String getSavedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, "en"); // default English
    }

    public static Context setLocale(Context context, String languageCode) {
        persist(context, languageCode);

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        android.content.res.Configuration config = new android.content.res.Configuration(
                context.getResources().getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            return context;
        }
    }

    private static void persist(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }
}
