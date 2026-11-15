package com.ares.callguard.domain.matching

import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform

/**
 * Decides whether a caller is one of the chosen people. Matching is deliberately exact:
 * a near miss must never ring through.
 */
class ContactMatcher(private val normalizer: PhoneNumberNormalizer) {

    fun matchPhoneNumber(incomingNumber: String?, people: List<Person>): Person? {
        val incoming = incomingNumber?.let(normalizer::normalize) ?: return null
        return people.firstOrNull { person ->
            person.enabled && person.phoneNumbers.any { normalizer.normalize(it) == incoming }
        }
    }

    fun matchDisplayName(platform: Platform, shownName: String?, people: List<Person>): Person? {
        val shown = shownName?.let(::comparableName)?.takeIf { it.isNotEmpty() } ?: return null
        return people.firstOrNull { person ->
            person.enabled && person.identities.any {
                it.platform == platform && comparableName(it.displayName) == shown
            }
        }
    }

    /** Another person already using this name on the same platform, if any. */
    fun findNameClash(
        platform: Platform,
        displayName: String,
        people: List<Person>,
        ignorePersonId: Long? = null,
    ): Person? {
        val wanted = comparableName(displayName)
        return people.firstOrNull { person ->
            person.id != ignorePersonId && person.identities.any {
                it.platform == platform && comparableName(it.displayName) == wanted
            }
        }
    }

    companion object {
        fun comparableName(name: String): String = name.trim().lowercase()
    }
}
