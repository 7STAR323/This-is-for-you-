package com.ares.callguard.domain.model

data class CallRecord(
    val id: Long = 0,
    val personName: String,
    val platform: Platform,
    val timeMillis: Long,
    val outcome: CallOutcome,
)

enum class CallOutcome {
    ANSWERED,
    ANSWERED_AFTER_ALERT,
    DECLINED,
    MISSED,
    STOPPED_BY_YOU,
}
