package com.ares.callguard.domain.matching

import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
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
        identities = listOf(PlatformIdentity(Platform.WHATSAPP, "Alishuu ❤️")),
    )
    private val dad = Person(
        id = 2,
        name = "Dad",
        phoneNumbers = listOf("080 2345 6789", "9123456780"),
    )
    private val people = listOf(alishuu, dad)

    @Test
    fun `phone number matches however it is written`() {
        assertEquals(alishuu, matcher.matchPhoneNumber("09876543210", people))
        assertEquals(alishuu, matcher.matchPhoneNumber("9876543210", people))
        assertEquals(dad, matcher.matchPhoneNumber("+918023456789", people))
    }

    @Test
    fun `any of a person's numbers matches`() {
        assertEquals(dad, matcher.matchPhoneNumber("+91 91234 56780", people))
    }

    @Test
    fun `an unknown number matches nobody`() {
        assertNull(matcher.matchPhoneNumber("+91 98765 43211", people))
    }

    @Test
    fun `a number that only shares its last digits does not match`() {
        assertNull(matcher.matchPhoneNumber("+44 9876543210", people))
    }

    @Test
    fun `withheld number matches nobody`() {
        assertNull(matcher.matchPhoneNumber(null, people))
        assertNull(matcher.matchPhoneNumber("", people))
    }

    @Test
    fun `disabled person is never matched`() {
        val disabled = listOf(alishuu.copy(enabled = false))
        assertNull(matcher.matchPhoneNumber("9876543210", disabled))
        assertNull(matcher.matchDisplayName(Platform.WHATSAPP, "Alishuu ❤️", disabled))
    }

    @Test
    fun `display name ignores case and surrounding spaces`() {
        assertEquals(alishuu, matcher.matchDisplayName(Platform.WHATSAPP, "  alishuu ❤️ ", people))
    }

    @Test
    fun `display name must otherwise be exact`() {
        assertNull(matcher.matchDisplayName(Platform.WHATSAPP, "Alishuu", people))
        assertNull(matcher.matchDisplayName(Platform.WHATSAPP, "Alishuu ❤️ (work)", people))
        assertNull(matcher.matchDisplayName(Platform.WHATSAPP, "Alishu ❤️", people))
    }

    @Test
    fun `contact name is not a platform identity`() {
        assertNull(matcher.matchDisplayName(Platform.WHATSAPP, "Dad", people))
    }

    @Test
    fun `blank display name matches nobody`() {
        val blankIdentity = dad.copy(identities = listOf(PlatformIdentity(Platform.WHATSAPP, " ")))
        assertNull(matcher.matchDisplayName(Platform.WHATSAPP, "", listOf(blankIdentity)))
        assertNull(matcher.matchDisplayName(Platform.WHATSAPP, null, people))
    }

    @Test
    fun `name clash is reported for another person only`() {
        assertEquals(alishuu, matcher.findNameClash(Platform.WHATSAPP, "ALISHUU ❤️", people))
        assertNull(matcher.findNameClash(Platform.WHATSAPP, "Alishuu ❤️", people, ignorePersonId = 1))
        assertNull(matcher.findNameClash(Platform.WHATSAPP, "Someone new", people))
    }
}
