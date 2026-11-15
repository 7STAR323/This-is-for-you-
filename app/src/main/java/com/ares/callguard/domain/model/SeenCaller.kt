package com.ares.callguard.domain.model

/** A caller name recently shown by a calling app, kept briefly so it can be picked in "Test match". */
data class SeenCaller(
    val platform: Platform,
    val displayName: String,
    val seenAtMillis: Long,
)
