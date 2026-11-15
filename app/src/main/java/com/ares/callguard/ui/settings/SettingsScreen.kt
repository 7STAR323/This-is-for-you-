package com.ares.callguard.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.callguard.domain.model.GuardSettings
import com.ares.callguard.domain.model.VibrationStyle
import com.ares.callguard.ui.components.AresTopBar
import com.ares.callguard.ui.components.LoadingState
import com.ares.callguard.ui.person.SwitchRow
import com.ares.callguard.ui.theme.Spacing
import kotlin.math.roundToInt

enum class SettingsPage(val title: String) {
    SETUP("Permissions and setup"),
    PLATFORMS("Supported apps"),
    DIAGNOSTICS("Test and diagnostics"),
    PRIVACY("What ARES can see"),
    ABOUT("About"),
}

@Composable
fun SettingsScreen(onOpen: (SettingsPage) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AresTopBar("Settings") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = settings
        if (current == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsCard {
                RingtoneRow(current.ringtoneUri, viewModel::setRingtone)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                VibrationChoice(current.vibrationStyle, viewModel::setVibrationStyle)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GracePeriod(current.graceSeconds, viewModel::setGraceSeconds)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "Turn the volume up for alerts",
                    description = "ARES raises the alarm volume while it alerts you, then puts it back.",
                    checked = current.raiseVolumeForAlerts,
                    onCheckedChange = viewModel::setRaiseVolume,
                )
            }

            SettingsCard {
                SettingsPage.entries.forEachIndexed { index, page ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkRow(page.title) { onOpen(page) }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { content() }
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun RingtoneRow(ringtoneUri: String?, onPicked: (String?) -> Unit) {
    val context = LocalContext.current
    val title = remember(ringtoneUri) { ringtoneTitle(context, ringtoneUri) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            onPicked(picked?.toString())
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) { picker.launch(ringtonePickerIntent(ringtoneUri)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Alert ringtone", style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}

private fun ringtonePickerIntent(currentUri: String?) =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri?.let(Uri::parse))

private fun ringtoneTitle(context: Context, uri: String?): String {
    if (uri == null) return "Your phone's ringtone"
    return runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri)).getTitle(context) }
        .getOrDefault("Your phone's ringtone")
}

@Composable
private fun VibrationChoice(selected: VibrationStyle, onSelect: (VibrationStyle) -> Unit) {
    Column(Modifier.padding(vertical = Spacing.sm)) {
        Text("Vibration", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            VibrationStyle.entries.forEach { style ->
                FilterChip(
                    selected = style == selected,
                    onClick = { onSelect(style) },
                    label = { Text(style.label) },
                )
            }
        }
    }
}

@Composable
private fun GracePeriod(seconds: Int, onChange: (Int) -> Unit) {
    val range = GuardSettings.GRACE_RANGE
    Column(Modifier.padding(vertical = Spacing.sm)) {
        Text("Wait before alerting", style = MaterialTheme.typography.titleMedium)
        Text(
            "$seconds seconds of normal ringing first",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.semantics { contentDescription = "Seconds to wait before alerting" },
        )
    }
}
