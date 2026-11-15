package com.ares.callguard.domain.model

/** Something the phone must allow before ARES can protect calls. Ordered as we ask for them. */
enum class SetupStep {
    SHOW_NOTIFICATIONS,
    SEE_PHONE_CALLS,
    SEE_APP_CALLS,
    FULL_SCREEN_ALERTS,
    KEEP_AWAKE,
}

enum class GuardStatus { ACTIVE, INACTIVE, NEEDS_ATTENTION }

data class GuardHealth(
    val status: GuardStatus,
    val missingSteps: List<SetupStep>,
) {
    companion object {
        fun from(guardWanted: Boolean, missingSteps: List<SetupStep>) = GuardHealth(
            status = when {
                !guardWanted -> GuardStatus.INACTIVE
                missingSteps.isNotEmpty() -> GuardStatus.NEEDS_ATTENTION
                else -> GuardStatus.ACTIVE
            },
            missingSteps = missingSteps,
        )
    }
}
