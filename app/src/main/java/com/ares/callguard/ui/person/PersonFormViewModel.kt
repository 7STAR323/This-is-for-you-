package com.ares.callguard.ui.person

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.callguard.data.contacts.ContactPicker
import com.ares.callguard.domain.matching.ContactMatcher
import com.ares.callguard.domain.matching.PhoneNumberNormalizer
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.PlatformIdentity
import com.ares.callguard.domain.model.PriorityLevel
import com.ares.callguard.domain.repository.PeopleRepository
import com.ares.callguard.domain.repository.SeenCallerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonForm(
    val isLoading: Boolean = false,
    val name: String = "",
    val photoPath: String? = null,
    val phoneNumbers: List<String> = listOf(""),
    val platforms: Set<Platform> = setOf(Platform.SIM, Platform.WHATSAPP),
    /** The exact name each app shows for this person. */
    val appNames: Map<Platform, String> = emptyMap(),
    val priority: PriorityLevel = PriorityLevel.RING_THROUGH,
    val enabled: Boolean = true,
    /** For each app, another person already using the same name there. */
    val nameClashes: Map<Platform, String> = emptyMap(),
    val isFinished: Boolean = false,
) {
    private val filledNumbers get() = phoneNumbers.filter(String::isNotBlank)

    /** What still stops this person from being saved, in words for the user; null when ready. */
    val problem: String?
        get() {
            val appWithoutIdentity = platforms.firstOrNull {
                it.isApp && filledNumbers.isEmpty() && appNames[it].isNullOrBlank()
            }
            return when {
                name.isBlank() -> "Add a name."
                platforms.isEmpty() -> "Choose at least one way they call you."
                Platform.SIM in platforms && filledNumbers.isEmpty() -> "Add a phone number for phone calls."
                appWithoutIdentity != null -> "Add the name ${appWithoutIdentity.label} shows for them."
                else -> null
            }
        }

    val canSave: Boolean get() = problem == null

    fun toPerson(id: Long) = Person(
        id = id,
        name = name,
        photoPath = photoPath,
        phoneNumbers = filledNumbers,
        platforms = platforms,
        identities = platforms.filter { it.isApp }.mapNotNull { platform ->
            appNames[platform]?.takeIf(String::isNotBlank)?.let { PlatformIdentity(platform, it) }
        },
        priority = priority,
        enabled = enabled,
    )
}

/** Backs both the Add Person flow and the edit screen; [personId] is absent when adding. */
@HiltViewModel
class PersonFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val people: PeopleRepository,
    private val matcher: ContactMatcher,
    private val normalizer: PhoneNumberNormalizer,
    private val contactPicker: ContactPicker,
    seenCallers: SeenCallerRepository,
) : ViewModel() {

    private val personId: Long? = savedState.get<Long>(PERSON_ID_ARG)

    private val _form = MutableStateFlow(PersonForm(isLoading = personId != null))
    val form: StateFlow<PersonForm> = _form.asStateFlow()

    /** Names each app recently showed for incoming calls, newest first. */
    val recentCallerNames: StateFlow<Map<Platform, List<String>>> =
        combine(Platform.apps.map { seenCallers.recent(it) }) { perApp ->
            Platform.apps.zip(perApp.map { callers -> callers.map { it.displayName } }).toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        if (personId != null) {
            viewModelScope.launch {
                val person = people.person(personId)
                _form.value = person?.toForm() ?: PersonForm(isFinished = true)
            }
        }
    }

    /** The international form a typed number will be matched as, or null if it isn't one yet. */
    fun internationalForm(number: String): String? =
        normalizer.normalize(number)?.takeIf { it.startsWith("+") }

    fun onContactPicked(contactUri: Uri) {
        viewModelScope.launch {
            val contact = contactPicker.read(contactUri) ?: return@launch
            _form.update { form ->
                form.copy(
                    name = contact.name,
                    photoPath = contact.photoPath,
                    phoneNumbers = listOfNotNull(contact.phoneNumber).ifEmpty { listOf("") },
                )
            }
        }
    }

    fun setName(name: String) = _form.update { it.copy(name = name) }

    fun setPhoneNumber(index: Int, number: String) = _form.update { form ->
        form.copy(phoneNumbers = form.phoneNumbers.toMutableList().also { it[index] = number })
    }

    fun addPhoneNumber() = _form.update { it.copy(phoneNumbers = it.phoneNumbers + "") }

    fun removePhoneNumber(index: Int) = _form.update { form ->
        form.copy(phoneNumbers = form.phoneNumbers.filterIndexed { i, _ -> i != index }.ifEmpty { listOf("") })
    }

    fun setPlatformEnabled(platform: Platform, enabled: Boolean) {
        _form.update { form ->
            form.copy(platforms = if (enabled) form.platforms + platform else form.platforms - platform)
        }
        suggestAppNames()
    }

    /**
     * Apps usually show the name saved in the phone's contacts, so that is offered as a
     * starting point for every chosen app that has no name yet.
     */
    fun suggestAppNames() {
        _form.update { form ->
            val suggestion = form.name.trim()
            if (suggestion.isEmpty()) return@update form
            val missing = form.platforms.filter { it.isApp && form.appNames[it].isNullOrBlank() }
            form.copy(appNames = form.appNames + missing.associateWith { suggestion })
        }
        checkNameClashes()
    }

    fun setAppName(platform: Platform, name: String) {
        _form.update { it.copy(appNames = it.appNames + (platform to name)) }
        checkNameClashes()
    }

    fun setPriority(priority: PriorityLevel) = _form.update { it.copy(priority = priority) }

    fun setEnabled(enabled: Boolean) = _form.update { it.copy(enabled = enabled) }

    fun save() {
        val form = _form.value
        if (!form.canSave) return
        viewModelScope.launch {
            people.save(form.toPerson(personId ?: 0))
            _form.update { it.copy(isFinished = true) }
        }
    }

    fun remove() {
        val id = personId ?: return
        viewModelScope.launch {
            people.remove(id)
            _form.update { it.copy(isFinished = true) }
        }
    }

    private fun checkNameClashes() {
        viewModelScope.launch {
            val everyone = people.currentPeople()
            val form = _form.value
            val clashes = form.platforms.filter { it.isApp }.mapNotNull { platform ->
                val name = form.appNames[platform].orEmpty()
                matcher.findNameClash(platform, name, everyone, ignorePersonId = personId)
                    ?.let { platform to it.name }
            }.toMap()
            _form.update { it.copy(nameClashes = clashes) }
        }
    }

    private fun Person.toForm() = PersonForm(
        name = name,
        photoPath = photoPath,
        phoneNumbers = phoneNumbers.ifEmpty { listOf("") },
        platforms = platforms,
        appNames = identities.associate { it.platform to it.displayName },
        priority = priority,
        enabled = enabled,
    )

    companion object {
        const val PERSON_ID_ARG = "personId"
    }
}
