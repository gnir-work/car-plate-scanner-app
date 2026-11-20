package com.lpscanner.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The language setting: follow the phone, or always Hebrew / English. */
enum class AppLanguage(val tag: String?) {
    System(null),
    Hebrew("he"),
    English("en");

    companion object {
        /** What the user chose. Android keeps it (and recreates the screen when it changes). */
        fun current(): AppLanguage {
            val chosen = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return System
            return if (chosen == "he" || chosen == "iw") Hebrew else entries.firstOrNull { it.tag == chosen } ?: System
        }

        fun set(language: AppLanguage) {
            val locales = language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}

/**
 * This context with the app's language, for texts made outside a screen (view models). On
 * Android 13+ the system already applies it app-wide; before that only to the screen.
 */
fun Context.inAppLanguage(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
    return createConfigurationContext(config)
}
