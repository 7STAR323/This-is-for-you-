package com.ares.callguard.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.callguard.domain.model.SetupStep
import com.ares.callguard.ui.components.AresTopBar
import com.ares.callguard.ui.components.LoadingState
import com.ares.callguard.ui.person.SwitchRow
import com.ares.callguard.ui.settings.SettingsCard
import com.ares.callguard.ui.settings.SettingsViewModel
import com.ares.callguard.ui.theme.Spacing

/**
 * The permission list. Shown once as a welcome ([onFinishWelcome] set) and afterwards from
 * Settings. Each permission is asked for on its own, with the reason next to it.
 */
@Composable
fun SetupScreen(
    onBack: (() -> Unit)?,
    onFinishWelcome: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val health by viewModel.health.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val openStep = rememberSetupLauncher(onReturned = viewModel::recheckSetup)
    val openAppSettings = rememberAppSettingsOpener()

    Scaffold(
        topBar = { AresTopBar(if (onFinishWelcome != null) "Welcome to ARES" else "Permissions and setup", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val missing = health?.missingSteps
        val currentSettings = settings
        if (missing == null || currentSettings == null) {
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
            Text(
                if (onFinishWelcome != null) {
                    "Your phone stays silent for the world. The few people you choose still get " +
                        "through. ARES needs your phone's permission for a few things first."
                } else if (missing.isEmpty()) {
                    "Everything ARES needs is allowed."
                } else {
                    "ARES can't protect your calls until these are allowed."
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )

            SetupStep.entries.forEach { step ->
                StepCard(step, isDone = step !in missing, onAllow = { openStep(step) })
            }

            KeepAwakeGuide(
                confirmed = currentSettings.keepAwakeStepsConfirmed,
                onConfirmedChange = viewModel::setKeepAwakeStepsConfirmed,
                onOpenAppSettings = openAppSettings,
            )

            if (onFinishWelcome != null) {
                Button(
                    onClick = {
                        viewModel.finishOnboarding()
                        onFinishWelcome()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(if (missing.isEmpty()) "Continue" else "Continue, I'll finish later")
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: SetupStep, isDone: Boolean, onAllow: () -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier.padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = if (isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isDone) "Done" else "Not done yet",
                tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    step.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isDone) {
                    FilledTonalButton(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Allow")
                    }
                }
            }
        }
    }
}

/** OPPO's own switches. Android gives apps no way to read them, so the user confirms. */
@Composable
private fun KeepAwakeGuide(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    SettingsCard {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Three OPPO switches", style = MaterialTheme.typography.titleMedium)
            Text(
                "OPPO phones close apps to save battery. ARES can't check these three for you, " +
                    "so please set them once:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GuideStep(1, "Open ARES's app settings and turn on \"Allow auto launch\" (Auto-start).")
            GuideStep(2, "In Battery usage, choose \"Allow background activity\" and no restrictions.")
            GuideStep(3, "Open recent apps, tap the menu on the ARES card and choose \"Lock\".")
            OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Open ARES app settings")
            }
            SwitchRow(title = "I've done these", checked = confirmed, onCheckedChange = onConfirmedChange)
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("$number.", style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
