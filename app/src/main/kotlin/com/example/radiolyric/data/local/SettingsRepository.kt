package com.example.radiolyric.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-level user preferences. */
data class AppSettings(
        val theme: ThemeMode = ThemeMode.SYSTEM,
        val fontScale: Float = 1.0f,
        val defaultStationSid: Int = DEFAULT_STATION_SID_HEART_UK,
        val keepScreenOnLyrics: Boolean = false,
) {
    companion object {
        /** Heart UK SId — matches `data.radio.Stations.HeartUK`. */
        const val DEFAULT_STATION_SID_HEART_UK: Int = 0xCFD1
    }
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT;

    companion object {
        fun fromString(raw: String?): ThemeMode =
                values().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Reads + writes [AppSettings] backed by Jetpack DataStore Preferences. */
@Singleton
class SettingsRepository
@Inject
constructor(
        @ApplicationContext private val ctx: Context,
) {

    private val store: DataStore<Preferences> = ctx.dataStore

    val settings: Flow<AppSettings> =
            store.data.map { prefs ->
                AppSettings(
                        theme = ThemeMode.fromString(prefs[KEY_THEME]),
                        fontScale = prefs[KEY_FONT_SCALE] ?: 1.0f,
                        defaultStationSid =
                                prefs[KEY_DEFAULT_STATION_SID]
                                        ?: AppSettings.DEFAULT_STATION_SID_HEART_UK,
                        keepScreenOnLyrics = prefs[KEY_KEEP_SCREEN_ON_LYRICS] ?: false,
                )
            }

    /**
     * Whether we have already prompted the user for the Android 13+
     * `POST_NOTIFICATIONS` runtime permission. Used to ensure we ask at most
     * once per install (subsequent re-asks must go through OS settings).
     */
    val notificationPermissionAsked: Flow<Boolean> =
            store.data.map { prefs -> prefs[KEY_NOTIFICATION_PERMISSION_ASKED] ?: false }

    suspend fun setNotificationPermissionAsked(value: Boolean) {
        store.edit { it[KEY_NOTIFICATION_PERMISSION_ASKED] = value }
    }

    suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setFontScale(scale: Float) {
        store.edit { it[KEY_FONT_SCALE] = scale }
    }

    suspend fun setDefaultStationSid(sid: Int) {
        store.edit { it[KEY_DEFAULT_STATION_SID] = sid }
    }

    suspend fun setKeepScreenOnLyrics(value: Boolean) {
        store.edit { it[KEY_KEEP_SCREEN_ON_LYRICS] = value }
    }

    private companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_DEFAULT_STATION_SID = intPreferencesKey("default_station_sid")
        private val KEY_KEEP_SCREEN_ON_LYRICS = booleanPreferencesKey("keep_screen_on_lyrics")
        private val KEY_NOTIFICATION_PERMISSION_ASKED =
                booleanPreferencesKey("notification_permission_asked")
    }
}
