package com.ares.callguard.guard

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface PhoneSoundState {
    /** True when an incoming call would not be heard: Silent, Vibrate or Do Not Disturb. */
    fun isQuiet(): Boolean

    /** True when a call is already in progress, so a loud alert would land in the user's ear. */
    fun isInCall(): Boolean
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

    override fun isInCall(): Boolean =
        audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
}
