package com.ares.callguard.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberNormalizerTest {

    private val indian = PhoneNumberNormalizer { "IN" }

    @Test
    fun `international, trunk-prefixed and bare numbers normalise to the same value`() {
        val expected = "+919876543210"
        assertEquals(expected, indian.normalize("+91 98765 43210"))
        assertEquals(expected, indian.normalize("098765 43210"))
        assertEquals(expected, indian.normalize("9876543210"))
        assertEquals(expected, indian.normalize("0091-98765-43210"))
    }

    @Test
    fun `punctuation and spacing are ignored`() {
        assertEquals("+919876543210", indian.normalize(" (98765) 432-10 "))
    }

    @Test
    fun `a foreign number keeps its own country code`() {
        assertEquals("+14155552671", indian.normalize("+1 415 555 2671"))
    }

    @Test
    fun `home region decides how a bare number is read`() {
        val british = PhoneNumberNormalizer { "gb" }
        assertEquals("+447911123456", british.normalize("07911 123456"))
    }

    @Test
    fun `short codes fall back to their digits`() {
        assertEquals("121", indian.normalize("121"))
    }

    @Test
    fun `withheld or empty numbers cannot be normalised`() {
        assertNull(indian.normalize(""))
        assertNull(indian.normalize("Private number"))
    }
}
