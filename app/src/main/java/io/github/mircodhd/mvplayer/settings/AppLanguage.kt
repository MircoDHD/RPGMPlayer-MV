// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import io.github.mircodhd.mvplayer.debug.DebugLog
import java.util.Locale

/**
 * The app's own language, independent of the system. Android 13+ keeps it itself
 * (LocaleManager), which also fills Settings → Apps → RPGM Player → Language
 * (`res/xml/locales_config.xml`). Older versions store it here and every activity applies
 * it in attachBaseContext via [wrap].
 */
object AppLanguage {
    /** Language tags with a translation; null in [current] means "follow the system". */
    val SUPPORTED = listOf("de", "en")

    private const val PREFS = "app_language"
    private const val KEY = "tag"

    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeUnless { it.isEmpty }?.get(0)?.language
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }

    fun set(activity: Activity, tag: String?) {
        DebugLog.i("Language", "App language set to ${tag ?: "system"}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activities.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { if (tag == null) remove(KEY) else putString(KEY, tag) }
            activity.recreate()
        }
    }

    /** Before Android 13: the base context of an activity in the chosen language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = current(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }
}
