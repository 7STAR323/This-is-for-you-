package com.ares.callguard.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ares.callguard.domain.model.GuardSettings
import com.ares.callguard.domain.model.VibrationStyle
import com.ares.callguard.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<GuardSettings> = dataStore.data.map(::read)

    override suspend fun update(change: (GuardSettings) -> GuardSettings) {
        dataStore.edit { prefs -> write(prefs, change(read(prefs))) }
    }

    private fun read(prefs: Preferences): GuardSettings {
        val defaults = GuardSettings()
        return GuardSettings(
            guardWanted = prefs[GUARD_WANTED] ?: defaults.guardWanted,
            onboardingSeen = prefs[ONBOARDING_SEEN] ?: defaults.onboardingSeen,
            graceSeconds = (prefs[GRACE_SECONDS] ?: defaults.graceSeconds)
                .coerceIn(GuardSettings.GRACE_RANGE),
            ringtoneUri = prefs[RINGTONE_URI],
            vibrationStyle = prefs[VIBRATION_STYLE]
                ?.let { saved -> VibrationStyle.entries.firstOrNull { it.name == saved } }
                ?: defaults.vibrationStyle,
            raiseVolumeForAlerts = prefs[RAISE_VOLUME] ?: defaults.raiseVolumeForAlerts,
            keepAwakeStepsConfirmed = prefs[KEEP_AWAKE_CONFIRMED] ?: defaults.keepAwakeStepsConfirmed,
        )
    }

    private fun write(prefs: MutablePreferences, settings: GuardSettings) {
        prefs[GUARD_WANTED] = settings.guardWanted
        prefs[ONBOARDING_SEEN] = settings.onboardingSeen
        prefs[GRACE_SECONDS] = settings.graceSeconds
        prefs[VIBRATION_STYLE] = settings.vibrationStyle.name
        prefs[RAISE_VOLUME] = settings.raiseVolumeForAlerts
        prefs[KEEP_AWAKE_CONFIRMED] = settings.keepAwakeStepsConfirmed
        if (settings.ringtoneUri == null) {
            prefs.remove(RINGTONE_URI)
        } else {
            prefs[RINGTONE_URI] = settings.ringtoneUri
        }
    }

    private companion object {
        val GUARD_WANTED = booleanPreferencesKey("guard_wanted")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val GRACE_SECONDS = intPreferencesKey("grace_seconds")
        val RINGTONE_URI = stringPreferencesKey("ringtone_uri")
        val VIBRATION_STYLE = stringPreferencesKey("vibration_style")
        val RAISE_VOLUME = booleanPreferencesKey("raise_volume_for_alerts")
        val KEEP_AWAKE_CONFIRMED = booleanPreferencesKey("keep_awake_steps_confirmed")
    }
}
