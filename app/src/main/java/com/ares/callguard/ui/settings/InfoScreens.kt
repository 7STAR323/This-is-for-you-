package com.ares.callguard.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.ui.components.AresTopBar
import com.ares.callguard.ui.theme.Spacing

@Composable
private fun InfoPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = { AresTopBar(title, onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) { content() }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    SettingsCard {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlatformsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val seenRinging = settings?.platformsSeenRinging.orEmpty()

    InfoPage(SettingsPage.PLATFORMS.title, onBack) {
        InfoCard(
            "Phone calls: works",
            "ARES recognises the caller by number and can tell answered, declined and missed apart.",
        )
        Platform.apps.forEach { platform ->
            InfoCard(
                title = platform.label + ": " +
                    if (platform in seenRinging) "seen ringing on this phone" else "not seen on this phone yet",
                body = appNote(platform) + " " +
                    if (platform in seenRinging) {
                        "ARES has recognised at least one ringing ${platform.label} call here."
                    } else {
                        "ARES is listening, but has not yet recognised a ringing ${platform.label} " +
                            "call on this phone, so treat it as unproven."
                    },
            )
        }
        InfoCard(
            "Facebook: not possible",
            "The Facebook app does not place calls itself; its calls happen in Messenger.",
        )
        InfoCard(
            "What all apps share",
            "Apps show ARES a name and almost never a number. A call answered on another device " +
                "looks the same as a declined one. ARES cannot see who is calling in any other way.",
        )
    }
}

private fun appNote(platform: Platform): String = when (platform) {
    Platform.WHATSAPP -> "Recognised from WhatsApp's call notification."
    Platform.TELEGRAM -> "Telegram uses Android's standard call notification, which ARES understands."
    Platform.MESSENGER -> "Recognised only if Messenger marks its notification as a call."
    Platform.INSTAGRAM -> "Instagram may announce calls as ordinary notifications, which ARES ignores on purpose."
    Platform.SNAPCHAT -> "Snapchat shares very little in its notifications, so this may never work."
    Platform.SIM -> ""
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) = InfoPage(SettingsPage.PRIVACY.title, onBack) {
    InfoCard(
        "Everything stays on this phone",
        "ARES has no account, no server and no internet permission. It cannot send anything " +
            "anywhere, and its data is left out of phone backups.",
    )
    InfoCard(
        "Phone calls",
        "ARES sees the number that is calling and checks your call log to learn whether a " +
            "call from one of your people was missed. It never answers, ends, records or redials a call.",
    )
    InfoCard(
        "Notifications",
        "Android hands ARES every notification, because that is how the permission works. " +
            "ARES throws away everything except call notifications from calling apps, without " +
            "reading or storing it. It never reads messages.",
    )
    InfoCard(
        "What is kept",
        "Your people, and a 30-day list of their calls: who, which app, when and what happened. " +
            "The names of recent app callers are kept for one day so you can pick the exact name. " +
            "The diagnostics list lives in memory only and never includes names, numbers or message text.",
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) = InfoPage(SettingsPage.ABOUT.title, onBack) {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    InfoCard(
        "ARES Call Guard",
        "Your phone stays silent for the world, and the few people you choose still get through.",
    )
    InfoCard("Version", version.orEmpty())
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val log by viewModel.detectionLog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lines = log.map { entry ->
        "${DateFormat.format("HH:mm:ss", entry.timeMillis)}  ${entry.message}"
    }

    InfoPage(SettingsPage.DIAGNOSTICS.title, onBack) {
        Button(
            onClick = viewModel::sendTestAlert,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) { Text("Send a test alert") }
        Text(
            "The test alert rings for 20 seconds or until you stop it. Lock your phone first to " +
                "see what a real alert looks like.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("What ARES noticed", style = MaterialTheme.typography.titleMedium)
        Text(
            "Every notification from a calling app is listed by its shape only: no names, " +
                "numbers or message text. The list is kept in memory while ARES is on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("ARES diagnostics", lines.joinToString("\n")))
                },
                enabled = lines.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Copy") }
            OutlinedButton(
                onClick = viewModel::clearDetectionLog,
                enabled = lines.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Clear") }
        }
        if (lines.isEmpty()) {
            Text(
                "Nothing yet. Turn ARES on, then ask someone to call you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
