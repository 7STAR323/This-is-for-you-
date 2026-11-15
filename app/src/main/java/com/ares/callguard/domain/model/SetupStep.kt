package com.ares.callguard.domain.model

/**
 * Something the phone must allow before ARES can protect calls, in the order we ask.
 * Optional steps improve the alert but protection works without them.
 */
enum class SetupStep(val isRequired: Boolean = true) {
    SHOW_NOTIFICATIONS,
    SEE_PHONE_CALLS,
    SEE_APP_CALLS,
    FULL_SCREEN_ALERTS,
    KEEP_AWAKE,
    DO_NOT_DISTURB_ACCESS(isRequired = false),
}

enum class GuardStatus { ACTIVE, INACTIVE, NEEDS_ATTENTION }

data class GuardHealth(
    val status: GuardStatus,
    /** Every step not yet done, required or optional. */
    val missingSteps: List<SetupStep>,
) {
    val missingRequiredSteps: List<SetupStep> get() = missingSteps.filter { it.isRequired }

    companion object {
        fun from(guardWanted: Boolean, missingSteps: List<SetupStep>) = GuardHealth(
            status = when {
                !guardWanted -> GuardStatus.INACTIVE
                missingSteps.any { it.isRequired } -> GuardStatus.NEEDS_ATTENTION
                else -> GuardStatus.ACTIVE
            },
            missingSteps = missingSteps,
        )
    }
}
