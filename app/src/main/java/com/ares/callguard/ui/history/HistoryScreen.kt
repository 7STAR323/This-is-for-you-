package com.ares.callguard.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ares.callguard.domain.model.CallOutcome
import com.ares.callguard.domain.model.CallRecord
import com.ares.callguard.domain.repository.HistoryRepository
import com.ares.callguard.guard.CallSessionManager
import com.ares.callguard.ui.components.AresTopBar
import com.ares.callguard.ui.components.EmptyState
import com.ares.callguard.ui.components.LoadingState
import com.ares.callguard.ui.people.PlatformIcon
import com.ares.callguard.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
) : ViewModel() {

    /** Null while loading. */
    val records: StateFlow<List<CallRecord>?> =
        history.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            history.removeOlderThan(System.currentTimeMillis() - CallSessionManager.HISTORY_RETENTION_MILLIS)
        }
    }

    fun clear() {
        viewModelScope.launch { history.clear() }
    }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AresTopBar("History") {
                if (!records.isNullOrEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val list = records
        when {
            list == null -> LoadingState(Modifier.padding(padding))

            list.isEmpty() -> EmptyState(
                title = "No important calls yet",
                message = "Calls from your people appear here. ARES keeps the last 30 days.",
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(list, key = CallRecord::id) { CallRecordRow(it) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes the list of past calls from ARES. Your people stay.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun CallRecordRow(record: CallRecord) {
    val context = LocalContext.current
    val time = DateUtils.getRelativeDateTimeString(
        context,
        record.timeMillis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.WEEK_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_ALL,
    ).toString()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PlatformIcon(record.platform)
            Column(Modifier.weight(1f)) {
                Text(record.personName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${record.platform.label}, $time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(record.outcome.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val CallOutcome.label: String
    get() = when (this) {
        CallOutcome.ANSWERED -> "Answered"
        CallOutcome.ANSWERED_AFTER_ALERT -> "Answered after alert"
        CallOutcome.DECLINED -> "Declined"
        CallOutcome.MISSED -> "Missed"
        CallOutcome.STOPPED_BY_YOU -> "Stopped by you"
    }
