// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import java.net.URI
import java.net.URISyntaxException

object SearchUrl {
    private const val PLACEHOLDER = "%s"
    private const val MAX_LENGTH = 2048
    private const val SENTINEL = "searchplaceholdersentinel"

    enum class Reason {
        BLANK,
        TOO_LONG,
        CONTROL_CHARACTER,
        NO_PLACEHOLDER,
        MULTIPLE_PLACEHOLDERS,
        UNSUPPORTED_SCHEME,
        USERINFO_NOT_ALLOWED,
        PERCENT_ESCAPE_IN_AUTHORITY,
        MISSING_HOST,
        PLACEHOLDER_IN_AUTHORITY,
        MALFORMED,
    }

    sealed interface Validation {
        data class Ok(val insecure: Boolean) : Validation
        data class Invalid(val reason: Reason) : Validation
    }

    fun validate(template: String): Validation {
        val trimmed = template.trim()
        if (trimmed.isEmpty()) return Validation.Invalid(Reason.BLANK)
        if (trimmed.length > MAX_LENGTH) return Validation.Invalid(Reason.TOO_LONG)
        if (trimmed.any { it.isIsoControl() }) return Validation.Invalid(Reason.CONTROL_CHARACTER)

        val placeholderCount = trimmed.windowed(PLACEHOLDER.length, step = 1).count { it == PLACEHOLDER }
        if (placeholderCount == 0) return Validation.Invalid(Reason.NO_PLACEHOLDER)
        if (placeholderCount > 1) return Validation.Invalid(Reason.MULTIPLE_PLACEHOLDERS)

        val substituted = trimmed.replaceFirst(PLACEHOLDER, SENTINEL)
        val uri = try {
            URI(substituted)
        } catch (e: URISyntaxException) {
            return Validation.Invalid(Reason.MALFORMED)
        }

        val scheme = uri.scheme ?: return Validation.Invalid(Reason.UNSUPPORTED_SCHEME)
        val insecure = when {
            scheme.equals("https", ignoreCase = true) -> false
            scheme.equals("http", ignoreCase = true) -> true
            else -> return Validation.Invalid(Reason.UNSUPPORTED_SCHEME)
        }

        val rawAuthority = uri.rawAuthority
        if (rawAuthority.isNullOrEmpty()) return Validation.Invalid(Reason.MISSING_HOST)
        if (rawAuthority.contains('@')) return Validation.Invalid(Reason.USERINFO_NOT_ALLOWED)
        if (rawAuthority.contains('%')) return Validation.Invalid(Reason.PERCENT_ESCAPE_IN_AUTHORITY)

        val host = if (rawAuthority.startsWith("[")) {
            val closingBracket = rawAuthority.indexOf(']')
            if (closingBracket < 0) rawAuthority else rawAuthority.substring(0, closingBracket + 1)
        } else {
            rawAuthority.substringBeforeLast(':')
        }
        if (host.isEmpty()) return Validation.Invalid(Reason.MISSING_HOST)

        val prefix = "$scheme://"
        if (!substituted.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            return Validation.Invalid(Reason.MALFORMED)
        }

        val authorityEnd = prefix.length + rawAuthority.length
        val sentinelIndex = substituted.indexOf(SENTINEL)
        if (sentinelIndex < authorityEnd) return Validation.Invalid(Reason.PLACEHOLDER_IN_AUTHORITY)

        return Validation.Ok(insecure = insecure)
    }

    fun build(template: String, query: String): String? {
        if (validate(template) !is Validation.Ok) return null
        if (query.isBlank()) return null

        val trimmed = template.trim()
        val schemeEnd = trimmed.indexOf(':')
        val normalized = trimmed.substring(0, schemeEnd).lowercase() + trimmed.substring(schemeEnd)
        return normalized.replaceFirst(PLACEHOLDER, encodeQueryComponent(query))
    }

    private fun Char.isIsoControl(): Boolean = Character.isISOControl(this)

    private const val HEX = "0123456789ABCDEF"

    private fun isUnreserved(byte: Int): Boolean {
        val c = byte.toChar()
        return c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~'
    }

    private fun encodeQueryComponent(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(bytes.size)
        for (raw in bytes) {
            val b = raw.toInt() and 0xFF
            if (isUnreserved(b)) {
                out.append(b.toChar())
            } else {
                out.append('%').append(HEX[b shr 4]).append(HEX[b and 0xF])
            }
        }
        return out.toString()
    }
}
