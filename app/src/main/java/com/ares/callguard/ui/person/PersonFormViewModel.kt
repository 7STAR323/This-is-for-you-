package com.ares.callguard.ui.person

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.callguard.data.contacts.ContactPicker
import com.ares.callguard.domain.matching.ContactMatcher
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonForm(
    val isLoading: Boolean = false,
    val name: String = "",
    val photoPath: String? = null,
    val phoneNumbers: List<String> = listOf(""),
    val whatsAppName: String = "",
    val priority: PriorityLevel = PriorityLevel.RING_THROUGH,
    val enabled: Boolean = true,
    /** Name of another person already using the same WhatsApp name, if any. */
    val sharesWhatsAppNameWith: String? = null,
    val isFinished: Boolean = false,
) {
    private val filledNumbers get() = phoneNumbers.filter(String::isNotBlank)

    val canSave: Boolean
        get() = name.isNotBlank() && (filledNumbers.isNotEmpty() || whatsAppName.isNotBlank())

    fun toPerson(id: Long) = Person(
        id = id,
        name = name,
        photoPath = photoPath,
        phoneNumbers = filledNumbers,
        identities = listOfNotNull(
            whatsAppName.takeIf(String::isNotBlank)?.let { PlatformIdentity(Platform.WHATSAPP, it) },
        ),
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
    private val contactPicker: ContactPicker,
    seenCallers: SeenCallerRepository,
) : ViewModel() {

    private val personId: Long? = savedState.get<Long>(PERSON_ID_ARG)

    private val _form = MutableStateFlow(PersonForm(isLoading = personId != null))
    val form: StateFlow<PersonForm> = _form.asStateFlow()

    /** Names WhatsApp recently showed for incoming calls, newest first. */
    val recentWhatsAppNames: StateFlow<List<String>> =
        seenCallers.recent(Platform.WHATSAPP)
            .map { callers -> callers.map { it.displayName } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (personId != null) {
            viewModelScope.launch {
                val person = people.person(personId)
                _form.value = person?.toForm() ?: PersonForm(isFinished = true)
            }
        }
    }

    fun onContactPicked(contactUri: Uri) {
        viewModelScope.launch {
            val contact = contactPicker.read(contactUri) ?: return@launch
            _form.update { form ->
                form.copy(
                    name = contact.name,
                    photoPath = contact.photoPath,
                    phoneNumbers = listOfNotNull(contact.phoneNumber).ifEmpty { listOf("") },
                    // WhatsApp shows the name saved in contacts, so that is the best first guess.
                    whatsAppName = form.whatsAppName.ifBlank { contact.name },
                )
            }
            checkWhatsAppNameClash()
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

    fun setWhatsAppName(name: String) {
        _form.update { it.copy(whatsAppName = name) }
        checkWhatsAppNameClash()
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

    private fun checkWhatsAppNameClash() {
        viewModelScope.launch {
            val name = _form.value.whatsAppName
            val clash = if (name.isBlank()) {
                null
            } else {
                matcher.findNameClash(Platform.WHATSAPP, name, people.currentPeople(), ignorePersonId = personId)
            }
            _form.update { it.copy(sharesWhatsAppNameWith = clash?.name) }
        }
    }

    private fun Person.toForm() = PersonForm(
        name = name,
        photoPath = photoPath,
        phoneNumbers = phoneNumbers.ifEmpty { listOf("") },
        whatsAppName = identities.firstOrNull { it.platform == Platform.WHATSAPP }?.displayName.orEmpty(),
        priority = priority,
        enabled = enabled,
    )

    companion object {
        const val PERSON_ID_ARG = "personId"
    }
}
