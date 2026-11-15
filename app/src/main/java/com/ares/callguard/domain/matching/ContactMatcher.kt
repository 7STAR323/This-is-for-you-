package com.ares.callguard.domain.matching

import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import java.text.Normalizer

/** Everything a call revealed about who is calling. Either list may be empty. */
data class CallerIdentity(
    val phoneNumbers: List<String> = emptyList(),
    val displayNames: List<String> = emptyList(),
)

enum class IdentifiedBy { PHONE_NUMBER, DISPLAY_NAME }

sealed interface MatchResult {
    data class Matched(val person: Person, val by: IdentifiedBy) : MatchResult

    /** More than one person fits, so nobody gets priority treatment. */
    data object Ambiguous : MatchResult

    /** We know who is calling, and they are not one of the chosen people. */
    data object NotOnList : MatchResult

    /** The call revealed neither a number nor a name. */
    data object Unidentified : MatchResult
}

/**
 * Decides whether a caller is one of the chosen people. A phone number is proof and always
 * wins. A display name is only what an app chose to show, so it is used when no number
 * contradicts it, must be exact, and can be switched off entirely.
 */
class ContactMatcher(private val normalizer: PhoneNumberNormalizer) {

    fun match(
        platform: Platform,
        caller: CallerIdentity,
        people: List<Person>,
        allowNameMatch: Boolean = true,
    ): MatchResult {
        val candidates = people.filter { it.enabled && platform in it.platforms }
        val numbers = caller.phoneNumbers.mapNotNull(normalizer::normalize).toSet()
        val names = caller.displayNames.map(::comparableName).filter(String::isNotEmpty).toSet()

        if (numbers.isNotEmpty()) {
            val byNumber = candidates.filter { person ->
                person.phoneNumbers.any { normalizer.normalize(it) in numbers }
            }
            if (byNumber.isNotEmpty()) return single(byNumber, IdentifiedBy.PHONE_NUMBER)
        }

        if (platform.isApp && allowNameMatch && names.isNotEmpty()) {
            val byName = candidates.filter { person ->
                // A revealed number that isn't theirs rules a person out, whatever the name says.
                val contradictedByNumber = numbers.isNotEmpty() && person.phoneNumbers.isNotEmpty()
                !contradictedByNumber && person.identities.any {
                    it.platform == platform && comparableName(it.displayName) in names
                }
            }
            if (byName.isNotEmpty()) return single(byName, IdentifiedBy.DISPLAY_NAME)
        }

        return if (numbers.isEmpty() && names.isEmpty()) MatchResult.Unidentified else MatchResult.NotOnList
    }

    /** Another person already using this name on the same platform, if any. */
    fun findNameClash(
        platform: Platform,
        displayName: String,
        people: List<Person>,
        ignorePersonId: Long? = null,
    ): Person? {
        val wanted = comparableName(displayName)
        if (wanted.isEmpty()) return null
        return people.firstOrNull { person ->
            person.id != ignorePersonId && person.identities.any {
                it.platform == platform && comparableName(it.displayName) == wanted
            }
        }
    }

    private fun single(matches: List<Person>, by: IdentifiedBy): MatchResult =
        matches.singleOrNull()?.let { MatchResult.Matched(it, by) } ?: MatchResult.Ambiguous

    companion object {
        /**
         * The form in which two names are compared. Apps wrap names in invisible
         * text-direction marks and sometimes use a different Unicode composition, so those are
         * removed; everything a person can actually see, emojis included, must still be equal.
         */
        fun comparableName(name: String): String =
            Normalizer.normalize(name, Normalizer.Form.NFC)
                .filterNot { Character.getType(it) == Character.FORMAT.toInt() }
                .trim()
                .replace(WHITESPACE_RUN, " ")
                .lowercase()

        private val WHITESPACE_RUN = Regex("\\s+")
    }
}
