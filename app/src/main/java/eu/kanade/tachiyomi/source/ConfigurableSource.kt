@file:Suppress("unused")

package eu.kanade.tachiyomi.source

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** A source with its own settings screen, built with AndroidX Preference. */
interface ConfigurableSource : Source {

    fun getSourcePreferences(): SharedPreferences =
        Injekt.get<Context>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

/** Same file name scheme as other Tachiyomi-family apps, so extension settings stay compatible. */
fun ConfigurableSource.preferenceKey(): String = "source_$id"

fun ConfigurableSource.sourcePreferences(): SharedPreferences =
    Injekt.get<Context>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

fun sourcePreferences(key: String): SharedPreferences =
    Injekt.get<Context>().getSharedPreferences(key, Context.MODE_PRIVATE)
