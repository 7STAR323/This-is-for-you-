package com.ares.callguard.guard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class DetectionLogEntry(val timeMillis: Long, val message: String)

/**
 * A short, memory-only trail of what the detectors saw, shown under Settings > Diagnostics.
 * It is never written to disk and disappears when the app's process ends.
 */
@Singleton
class DetectionLog @Inject constructor() {

    private val _entries = MutableStateFlow<List<DetectionLogEntry>>(emptyList())
    val entries: StateFlow<List<DetectionLogEntry>> = _entries.asStateFlow()

    fun add(message: String) {
        val entry = DetectionLogEntry(System.currentTimeMillis(), message)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private companion object {
        const val MAX_ENTRIES = 150
    }
}
