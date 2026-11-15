package com.ares.callguard.domain.model

data class CallRecord(
    val id: Long = 0,
    val personName: String,
    val platform: Platform,
    val timeMillis: Long,
    val outcome: CallOutcome,
)

enum class CallOutcome(val alertWasShown: Boolean, val wasMissed: Boolean) {
    ANSWERED(alertWasShown = false, wasMissed = false),
    ANSWERED_AFTER_ALERT(alertWasShown = true, wasMissed = false),
    DECLINED(alertWasShown = false, wasMissed = false),
    MISSED(alertWasShown = false, wasMissed = true),
    MISSED_AFTER_ALERT(alertWasShown = true, wasMissed = true),
    STOPPED_BY_YOU(alertWasShown = true, wasMissed = false),
}
