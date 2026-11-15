package com.ares.callguard.ui.setup

import com.ares.callguard.domain.model.SetupStep

val SetupStep.title: String
    get() = when (this) {
        SetupStep.SHOW_NOTIFICATIONS -> "Allow notifications"
        SetupStep.SEE_PHONE_CALLS -> "Let ARES see who is calling"
        SetupStep.SEE_APP_CALLS -> "Let ARES notice WhatsApp calls"
        SetupStep.FULL_SCREEN_ALERTS -> "Allow special alerts"
        SetupStep.KEEP_AWAKE -> "Keep ARES awake"
    }

val SetupStep.reason: String
    get() = when (this) {
        SetupStep.SHOW_NOTIFICATIONS ->
            "So ARES can alert you and tell you about a missed call."
        SetupStep.SEE_PHONE_CALLS ->
            "ARES compares the caller's number with your people, and checks afterwards whether " +
                "the call was missed. Your phone will ask about Phone and Call logs. " +
                "ARES never answers, ends or records a call."
        SetupStep.SEE_APP_CALLS ->
            "WhatsApp announces calls through notifications. Your phone will warn that ARES can " +
                "read all notifications; ARES only looks at WhatsApp call notifications and " +
                "drops everything else straight away."
        SetupStep.FULL_SCREEN_ALERTS ->
            "So an important call can light up the whole screen, even when the phone is locked."
        SetupStep.KEEP_AWAKE ->
            "Stops your phone putting ARES to sleep to save battery."
    }

fun stepsLeftMessage(missing: List<SetupStep>): String {
    val count = missing.size
    return "$count ${if (count == 1) "step" else "steps"} left: ${missing.first().title}"
}
