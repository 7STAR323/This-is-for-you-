package com.ares.callguard.guard

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.ares.callguard.domain.model.Platform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface PhoneSoundState {
    /** True when an incoming call would not be heard: Silent, Vibrate or Do Not Disturb. */
    fun isQuiet(): Boolean

    /**
     * True when the user is already talking on another call, so a loud alert for a new call
     * on [ringingPlatform] would land in their ear.
     */
    fun isOnAnotherCall(ringingPlatform: Platform): Boolean

    /** True when Do Not Disturb is on in a form that also mutes alarms, and so mutes the alert. */
    fun doNotDisturbSilencesAlert(): Boolean
}

class AndroidPhoneSoundState @Inject constructor(
    @ApplicationContext context: Context,
) : PhoneSoundState {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    override fun isQuiet(): Boolean =
        audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            audio.getStreamVolume(AudioManager.STREAM_RING) == 0 ||
            notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    // A calling app can switch the phone to "in communication" while its own call is still
    // only ringing, so for app calls that mode proves nothing; a cellular call always does.
    override fun isOnAnotherCall(ringingPlatform: Platform): Boolean = when (audio.mode) {
        AudioManager.MODE_IN_CALL -> true
        AudioManager.MODE_IN_COMMUNICATION -> ringingPlatform == Platform.SIM
        else -> false
    }

    override fun doNotDisturbSilencesAlert(): Boolean =
        when (notifications.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> true
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> prioritySilencesAlarms()
            else -> false
        }

    // Reading the Do Not Disturb rules needs the user's permission; without it we can't tell.
    private fun prioritySilencesAlarms(): Boolean = runCatching {
        val allowed = notifications.notificationPolicy.priorityCategories
        allowed and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0
    }.getOrDefault(false)
}
