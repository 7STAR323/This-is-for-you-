package com.ares.callguard.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.callguard.ui.components.AresTopBar
import com.ares.callguard.ui.components.LoadingState
import com.ares.callguard.ui.theme.Spacing

@Composable
fun EditPersonScreen(onDone: () -> Unit, viewModel: PersonFormViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val recentNames by viewModel.recentCallerNames.collectAsStateWithLifecycle()
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(form.isFinished) { if (form.isFinished) onDone() }

    Scaffold(
        topBar = {
            AresTopBar(form.name.ifBlank { "Person" }, onBack = onDone)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (form.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            NameField(form.name, viewModel::setName)

            SwitchRow(
                title = "Can reach you",
                checked = form.enabled,
                onCheckedChange = viewModel::setEnabled,
            )

            SectionTitle("When they call")
            PriorityChoice(form.priority, viewModel::setPriority)

            SectionTitle("Phone numbers")
            PhoneNumberFields(
                numbers = form.phoneNumbers,
                internationalForm = viewModel::internationalForm,
                onChange = viewModel::setPhoneNumber,
                onAdd = viewModel::addPhoneNumber,
                onRemove = viewModel::removePhoneNumber,
            )

            SectionTitle("How they call you")
            PlatformChooser(
                form = form,
                recentNames = recentNames,
                onToggle = viewModel::setPlatformEnabled,
                onNameChange = viewModel::setAppName,
            )

            form.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = viewModel::save,
                enabled = form.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text("Save") }

            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Remove ${form.name.trim()}", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${form.name.trim()}?") },
            text = { Text("Their calls will behave like everyone else's.") },
            confirmButton = { TextButton(onClick = viewModel::remove) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}
