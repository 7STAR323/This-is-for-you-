package com.ares.callguard.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.ares.callguard.domain.model.GuardSettings
import com.ares.callguard.domain.model.VibrationStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface AlertPlayer {
    fun start(settings: GuardSettings, vibrateOnly: Boolean)
    fun stop()
    fun playMissedCallChime(settings: GuardSettings, vibrateOnly: Boolean)
}

@Singleton
class AndroidAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlertPlayer {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator =
        context.getSystemService(VibratorManager::class.java).defaultVibrator

    // The alarm stream is the one Android keeps audible in Silent and Vibrate, and in
    // Do Not Disturb unless the user has chosen to silence alarms too. We deliberately don't
    // ask for audio focus: that could make the calling app stop its own ringing.
    private val alarmAudio = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var ringtone: MediaPlayer? = null
    private var chime: MediaPlayer? = null
    private var alarmVolumeBeforeAlert: Int? = null

    override fun start(settings: GuardSettings, vibrateOnly: Boolean) {
        stop()
        vibrate(settings.vibrationStyle.repeatingPattern(), repeat = true)
        if (vibrateOnly) return

        if (settings.raiseVolumeForAlerts) raiseAlarmVolume()
        ringtone = play(ringtoneCandidates(settings), looping = true)
    }

    override fun stop() {
        vibrator.cancel()
        ringtone?.release()
        ringtone = null
        restoreAlarmVolume()
    }

    override fun playMissedCallChime(settings: GuardSettings, vibrateOnly: Boolean) {
        vibrate(MISSED_CALL_PATTERN, repeat = false)
        if (vibrateOnly) return

        chime?.release()
        chime = play(
            listOf(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)),
            looping = false,
        )?.apply {
            setOnCompletionListener {
                it.release()
                if (chime === it) chime = null
            }
        }
    }

    private fun ringtoneCandidates(settings: GuardSettings): List<Uri?> = listOf(
        settings.ringtoneUri?.let(Uri::parse),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
        Settings.System.DEFAULT_ALARM_ALERT_URI,
    )

    /** Plays the first sound that works; a deleted or unreadable ringtone falls through. */
    private fun play(candidates: List<Uri?>, looping: Boolean): MediaPlayer? {
        for (uri in candidates.filterNotNull()) {
            val player = MediaPlayer()
            val started = runCatching {
                player.setAudioAttributes(alarmAudio)
                player.setDataSource(context, uri)
                player.isLooping = looping
                player.prepare()
                player.start()
            }.isSuccess
            if (started) return player
            player.release()
        }
        return null
    }

    private fun raiseAlarmVolume() {
        alarmVolumeBeforeAlert = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        }
    }

    private fun restoreAlarmVolume() {
        val previous = alarmVolumeBeforeAlert ?: return
        alarmVolumeBeforeAlert = null
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0) }
    }

    private fun vibrate(timings: LongArray, repeat: Boolean) {
        val effect = VibrationEffect.createWaveform(timings, if (repeat) 0 else -1)
        // Alarm-class vibration is the kind that still runs in Do Not Disturb and battery saver.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, alarmAudio)
        }
    }

    private fun VibrationStyle.repeatingPattern(): LongArray = when (this) {
        VibrationStyle.STRONG -> longArrayOf(0, 900, 400)
        VibrationStyle.HEARTBEAT -> longArrayOf(0, 180, 140, 320, 900)
        VibrationStyle.RAPID -> longArrayOf(0, 220, 120)
    }

    private companion object {
        val MISSED_CALL_PATTERN = longArrayOf(0, 250, 150, 250, 150, 500)
    }
}
