package com.ares.callguard.domain.model

data class GuardSettings(
    val guardWanted: Boolean = false,
    val onboardingSeen: Boolean = false,
    val graceSeconds: Int = DEFAULT_GRACE_SECONDS,
    val ringtoneUri: String? = null,
    val vibrationStyle: VibrationStyle = VibrationStyle.STRONG,
    val raiseVolumeForAlerts: Boolean = true,
    val keepAwakeStepsConfirmed: Boolean = false,
) {
    companion object {
        const val DEFAULT_GRACE_SECONDS = 10
        val GRACE_RANGE = 5..30
    }
}

enum class VibrationStyle(val label: String) {
    STRONG("Strong"),
    HEARTBEAT("Heartbeat"),
    RAPID("Rapid"),
}
