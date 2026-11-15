package com.ares.callguard.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.PriorityLevel
import com.ares.callguard.domain.repository.PeopleRepository
import com.ares.callguard.ui.components.AresTopBar
import com.ares.callguard.ui.components.EmptyState
import com.ares.callguard.ui.components.LoadingState
import com.ares.callguard.ui.components.PersonAvatar
import com.ares.callguard.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    /** Null while loading. */
    val people: StateFlow<List<Person>?> =
        repository.people.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(person: Person, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(person.id, enabled) }
    }
}

@Composable
fun PeopleScreen(
    onAddPerson: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val people by viewModel.people.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AresTopBar("People who can reach you") },
        floatingActionButton = {
            if (!people.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddPerson,
                    icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                    text = { Text("Add Person") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val list = people
        when {
            list == null -> LoadingState(Modifier.padding(padding))

            list.isEmpty() -> EmptyState(
                title = "Nobody yet",
                message = "Add the few people whose calls you never want to miss.",
                modifier = Modifier.padding(padding),
            ) {
                Button(onClick = onAddPerson) { Text("Add Person") }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(list, key = Person::id) { person ->
                    PersonCard(
                        person = person,
                        onClick = { onOpenPerson(person.id) },
                        onEnabledChange = { viewModel.setEnabled(person, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonCard(person: Person, onClick: () -> Unit, onEnabledChange: (Boolean) -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PersonAvatar(person.name, person.photoPath, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(person.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = when (person.priority) {
                        PriorityLevel.RING_THROUGH -> "Rings through"
                        PriorityLevel.MISSED_ALERT_ONLY -> "Missed-call alert only"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    person.platforms.sorted().forEach { PlatformIcon(it) }
                }
            }
            Switch(
                checked = person.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics {
                    contentDescription = "${person.name} can reach you"
                },
            )
        }
    }
}

@Composable
fun PlatformIcon(platform: Platform, modifier: Modifier = Modifier) {
    Icon(
        imageVector = when (platform) {
            Platform.SIM -> Icons.Rounded.Call
            Platform.WHATSAPP -> Icons.AutoMirrored.Rounded.Chat
            Platform.TELEGRAM -> Icons.AutoMirrored.Rounded.Send
            Platform.MESSENGER -> Icons.Rounded.Forum
            Platform.INSTAGRAM -> Icons.Rounded.PhotoCamera
            Platform.SNAPCHAT -> Icons.Rounded.Bolt
        },
        contentDescription = platform.label,
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier.size(18.dp),
    )
}
