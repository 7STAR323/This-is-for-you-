package com.ares.callguard.ui.person

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.callguard.ui.components.AresTopBar
import com.ares.callguard.ui.components.PersonAvatar
import com.ares.callguard.ui.theme.Spacing

private const val STEP_COUNT = 4

@Composable
fun AddPersonScreen(onDone: () -> Unit, viewModel: PersonFormViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val recentNames by viewModel.recentWhatsAppNames.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }

    val pickContact = rememberLauncherForActivityResult(PickContactPhone()) { uri ->
        if (uri != null) {
            viewModel.onContactPicked(uri)
            step = 1
        }
    }

    LaunchedEffect(form.isFinished) { if (form.isFinished) onDone() }
    BackHandler(enabled = step > 0) { step-- }

    Scaffold(
        topBar = { AresTopBar("Add Person", onBack = { if (step > 0) step-- else onDone() }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = Spacing.lg),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1) / STEP_COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Step ${step + 1} of $STEP_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when (step) {
                    0 -> {
                        StepHeading("Who should always reach you?")
                        OutlinedButton(
                            onClick = { pickContact.launch(Unit) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                        ) {
                            Icon(Icons.Rounded.Contacts, contentDescription = null)
                            Spacer(Modifier.size(Spacing.sm))
                            Text("Pick from contacts")
                        }
                        Text(
                            "or type a name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        NameField(form.name, viewModel::setName)
                    }

                    1 -> {
                        StepHeading("Which numbers does ${form.name.trim()} call from?")
                        PhoneNumberFields(
                            numbers = form.phoneNumbers,
                            onChange = viewModel::setPhoneNumber,
                            onAdd = viewModel::addPhoneNumber,
                            onRemove = viewModel::removePhoneNumber,
                        )
                    }

                    2 -> {
                        StepHeading("Do they call you on WhatsApp?")
                        Text(
                            "Leave this empty if they don't.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        WhatsAppNameField(
                            name = form.whatsAppName,
                            onChange = viewModel::setWhatsAppName,
                            recentNames = recentNames,
                            sharedWith = form.sharesWhatsAppNameWith,
                        )
                    }

                    else -> Summary(form)
                }
            }

            Button(
                onClick = { if (step < STEP_COUNT - 1) step++ else viewModel.save() },
                enabled = if (step < STEP_COUNT - 1) form.name.isNotBlank() else form.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md)
                    .heightIn(min = 56.dp),
            ) {
                Text(if (step < STEP_COUNT - 1) "Next" else "Save")
            }
        }
    }
}

@Composable
private fun StepHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun Summary(form: PersonForm) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PersonAvatar(form.name, form.photoPath, size = 72.dp)
        Text(form.name.trim(), style = MaterialTheme.typography.headlineMedium)
    }

    if (!form.canSave) {
        Text(
            "Add a phone number or a WhatsApp name so ARES can recognise their calls.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    val person = form.toPerson(id = 0)
    person.phoneNumbers.forEach { SummaryLine("Phone", it) }
    person.identities.forEach { SummaryLine(it.platform.label, it.displayName) }
    Text(
        "Their calls will ring through, even when your phone is silent. You can change this later.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
