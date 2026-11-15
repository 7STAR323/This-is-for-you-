package com.ares.callguard.domain.matching

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat

/**
 * Turns a number as dialled or as shown by the network into one comparable form, so that
 * "+91 98765 43210", "098765 43210" and "9876543210" are the same caller on an Indian SIM.
 */
class PhoneNumberNormalizer(private val homeRegion: () -> String) {

    private val phoneUtil = PhoneNumberUtil.getInstance()

    fun normalize(rawNumber: String): String? {
        val trimmed = rawNumber.trim()
        if (trimmed.none(Char::isDigit)) return null

        return try {
            val parsed = phoneUtil.parse(trimmed, homeRegion().uppercase())
            if (phoneUtil.isPossibleNumber(parsed)) {
                phoneUtil.format(parsed, PhoneNumberFormat.E164)
            } else {
                digitsOnly(trimmed)
            }
        } catch (_: NumberParseException) {
            digitsOnly(trimmed)
        }
    }

    // Short codes and oddly formatted numbers still deserve an exact match on their digits.
    private fun digitsOnly(number: String): String? =
        number.filter(Char::isDigit).takeIf { it.isNotEmpty() }
}
