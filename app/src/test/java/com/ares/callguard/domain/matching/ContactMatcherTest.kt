package com.ares.callguard.domain.matching

import com.ares.callguard.domain.matching.MatchResult.Ambiguous
import com.ares.callguard.domain.matching.MatchResult.Matched
import com.ares.callguard.domain.matching.MatchResult.NotOnList
import com.ares.callguard.domain.matching.MatchResult.Unidentified
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.Platform.SIM
import com.ares.callguard.domain.model.Platform.TELEGRAM
import com.ares.callguard.domain.model.Platform.WHATSAPP
import com.ares.callguard.domain.model.PlatformIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactMatcherTest {

    private val matcher = ContactMatcher(PhoneNumberNormalizer { "IN" })

    private val alishuu = Person(
        id = 1,
        name = "Alishuu",
        phoneNumbers = listOf("+91 98765 43210"),
        platforms = setOf(SIM, WHATSAPP),
        identities = listOf(PlatformIdentity(WHATSAPP, "Alishuu ❤️")),
    )
    private val dad = Person(
        id = 2,
        name = "Dad",
        phoneNumbers = listOf("080 2345 6789", "9123456780"),
        platforms = setOf(SIM),
    )
    private val people = listOf(alishuu, dad)

    private fun number(value: String) = CallerIdentity(phoneNumbers = listOf(value))
    private fun name(value: String) = CallerIdentity(displayNames = listOf(value))

    @Test
    fun `phone number matches however it is written`() {
        val byNumber = Matched(alishuu, IdentifiedBy.PHONE_NUMBER)
        assertEquals(byNumber, matcher.match(SIM, number("09876543210"), people))
        assertEquals(byNumber, matcher.match(SIM, number("9876543210"), people))
        assertEquals(byNumber, matcher.match(SIM, number("+91 98765-43210"), people))
    }

    @Test
    fun `any of a person's numbers matches`() {
        assertEquals(Matched(dad, IdentifiedBy.PHONE_NUMBER), matcher.match(SIM, number("+91 91234 56780"), people))
        assertEquals(Matched(dad, IdentifiedBy.PHONE_NUMBER), matcher.match(SIM, number("+918023456789"), people))
    }

    @Test
    fun `an unknown or foreign look-alike number is not on the list`() {
        assertEquals(NotOnList, matcher.match(SIM, number("+91 98765 43211"), people))
        assertEquals(NotOnList, matcher.match(SIM, number("+44 9876543210"), people))
    }

    @Test
    fun `a hidden caller is unidentified`() {
        assertEquals(Unidentified, matcher.match(SIM, CallerIdentity(), people))
        assertEquals(Unidentified, matcher.match(SIM, number(""), people))
        assertEquals(Unidentified, matcher.match(WHATSAPP, name("  "), people))
    }

    @Test
    fun `a number saved for two people is ambiguous and matches nobody`() {
        val twin = dad.copy(id = 3, name = "Twin", phoneNumbers = listOf("9876543210"))
        assertEquals(Ambiguous, matcher.match(SIM, number("9876543210"), people + twin))
    }

    @Test
    fun `a disabled person is never matched`() {
        val disabled = listOf(alishuu.copy(enabled = false))
        assertEquals(NotOnList, matcher.match(SIM, number("9876543210"), disabled))
        assertEquals(NotOnList, matcher.match(WHATSAPP, name("Alishuu ❤️"), disabled))
    }

    @Test
    fun `a person is only matched on the platforms chosen for them`() {
        assertEquals(NotOnList, matcher.match(WHATSAPP, number("+918023456789"), people))
        assertEquals(NotOnList, matcher.match(TELEGRAM, name("Alishuu ❤️"), people))
    }

    @Test
    fun `an app call is matched by number when the app reveals it`() {
        val caller = CallerIdentity(phoneNumbers = listOf("+919876543210"), displayNames = listOf("Someone else"))
        assertEquals(Matched(alishuu, IdentifiedBy.PHONE_NUMBER), matcher.match(WHATSAPP, caller, people))
    }

    @Test
    fun `without a number an app call falls back to the exact saved name`() {
        assertEquals(Matched(alishuu, IdentifiedBy.DISPLAY_NAME), matcher.match(WHATSAPP, name("Alishuu ❤️"), people))
    }

    @Test
    fun `same display name with a different number is not that person`() {
        val impostor = CallerIdentity(phoneNumbers = listOf("+91 70000 00000"), displayNames = listOf("Alishuu ❤️"))
        assertEquals(NotOnList, matcher.match(WHATSAPP, impostor, people))
    }

    @Test
    fun `a revealed number does not block a person saved by name only`() {
        val nameOnly = Person(
            id = 4,
            name = "Mira",
            platforms = setOf(WHATSAPP),
            identities = listOf(PlatformIdentity(WHATSAPP, "Mira")),
        )
        val caller = CallerIdentity(phoneNumbers = listOf("+91 70000 00000"), displayNames = listOf("Mira"))
        assertEquals(Matched(nameOnly, IdentifiedBy.DISPLAY_NAME), matcher.match(WHATSAPP, caller, people + nameOnly))
    }

    @Test
    fun `a changed display name no longer matches`() {
        assertEquals(NotOnList, matcher.match(WHATSAPP, name("Alishuu"), people))
        assertEquals(NotOnList, matcher.match(WHATSAPP, name("Alishu ❤️"), people))
        assertEquals(NotOnList, matcher.match(WHATSAPP, name("Alishuu ❤️ (work)"), people))
    }

    @Test
    fun `name matching ignores case, spacing and invisible direction marks`() {
        val expected = Matched(alishuu, IdentifiedBy.DISPLAY_NAME)
        assertEquals(expected, matcher.match(WHATSAPP, name("  alishuu  ❤️ "), people))
        assertEquals(expected, matcher.match(WHATSAPP, name("‪Alishuu ❤️‬"), people))
        assertEquals(expected, matcher.match(WHATSAPP, name("⁨Alishuu ❤️⁩‎"), people))
    }

    @Test
    fun `two people sharing an app name are ambiguous`() {
        val other = Person(
            id = 5,
            name = "Other",
            platforms = setOf(WHATSAPP),
            identities = listOf(PlatformIdentity(WHATSAPP, "alishuu ❤️")),
        )
        assertEquals(Ambiguous, matcher.match(WHATSAPP, name("Alishuu ❤️"), people + other))
    }

    @Test
    fun `a contact name alone is not an app identity`() {
        assertEquals(NotOnList, matcher.match(WHATSAPP, name("Dad"), people))
    }

    @Test
    fun `names are never used when number-only matching is switched on`() {
        assertEquals(NotOnList, matcher.match(WHATSAPP, name("Alishuu ❤️"), people, allowNameMatch = false))
    }

    @Test
    fun `a name never identifies a SIM caller`() {
        assertEquals(NotOnList, matcher.match(SIM, name("Alishuu ❤️"), people))
    }

    @Test
    fun `name clash is reported for another person only`() {
        assertEquals(alishuu, matcher.findNameClash(WHATSAPP, "ALISHUU ❤️", people))
        assertNull(matcher.findNameClash(WHATSAPP, "Alishuu ❤️", people, ignorePersonId = 1))
        assertNull(matcher.findNameClash(WHATSAPP, "Someone new", people))
        assertNull(matcher.findNameClash(Platform.TELEGRAM, "Alishuu ❤️", people))
    }
}
