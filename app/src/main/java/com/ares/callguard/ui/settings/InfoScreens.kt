package com.ares.callguard.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun PlatformsScreen(onBack: () -> Unit) = InfoPage(SettingsPage.PLATFORMS.title, onBack) {
    InfoCard(
        "Phone calls: works",
        "ARES recognises the caller by number and can tell answered, declined and missed apart.",
    )
    InfoCard(
        "WhatsApp: works, by name only",
        "WhatsApp shows ARES a name, not a number, so the name you save must match exactly. " +
            "A call answered on another device looks the same as a declined one. If WhatsApp " +
            "is not in English, a call you never saw ring may not be reported as missed.",
    )
    InfoCard(
        "Telegram, Instagram, Messenger, Snapchat: not yet",
        "These are planned. Until then ARES ignores them completely.",
    )
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) = InfoPage(SettingsPage.PRIVACY.title, onBack) {
    InfoCard(
        "Everything stays on this phone",
        "ARES has no account, no server and no internet permission. It cannot send anything anywhere.",
    )
    InfoCard(
        "Phone calls",
        "ARES sees the number that is calling and checks your call log to learn whether a " +
            "call from one of your people was missed. It never answers, ends, records or redials a call.",
    )
    InfoCard(
        "Notifications",
        "Android hands ARES every notification, because that is how the permission works. " +
            "ARES throws away everything except call notifications from WhatsApp, without " +
            "reading or storing it. It never reads messages.",
    )
    InfoCard(
        "What is kept",
        "Your people, and a 30-day list of their calls: who, which app, when and what happened. " +
            "The names of recent WhatsApp callers are kept for one day so you can pick the exact name.",
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

        Text("What ARES noticed recently", style = MaterialTheme.typography.titleMedium)
        if (log.isEmpty()) {
            Text(
                "Nothing yet. Calls ARES notices while it is on are listed here until the app is closed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        log.forEach { entry ->
            val time = DateFormat.getTimeFormat(context).format(entry.timeMillis)
            Text("$time  ${entry.message}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
