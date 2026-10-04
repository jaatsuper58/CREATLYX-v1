package com.chattlyx.core.common.phonenumber

/**
 * Lightweight E.164 normalisation used before the full libphonenumber
 * integration lands with AUTH-02 (Phase 1 keeps the Android-optimised build
 * to protect APK size).
 */
object E164 {

    const val MIN_LENGTH = 5
    const val MAX_LENGTH = 15

    private val SEPARATORS = Regex("[\\s\\-()./]")

    /**
     * Normalises a user-entered number to E.164 given the SIM/network region's
     * dial code (e.g. "+91"). Returns null when the input cannot be a valid
     * E.164 number.
     */
    fun normalize(rawNumber: String, regionDialCode: String): String? {
        val cleaned = SEPARATORS.replace(rawNumber, "")
        if (cleaned.isEmpty()) return null

        val candidate = when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.removePrefix("00")
            cleaned.startsWith("0") -> regionDialCode + cleaned.removePrefix("0")
            else -> regionDialCode + cleaned
        }

        return if (isValid(candidate)) candidate else null
    }

    /** Structural E.164 check: + followed by 5-15 digits, no leading zero. */
    fun isValid(e164: String): Boolean {
        if (!e164.startsWith("+")) return false
        val digits = e164.substring(1)
        if (digits.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (digits.any { !it.isDigit() }) return false
        if (digits.startsWith("0")) return false
        return true
    }
}
