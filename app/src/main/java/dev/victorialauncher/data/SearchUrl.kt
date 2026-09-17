// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import java.net.URI
import java.net.URISyntaxException

/**
 * Turns a URL template the user typed into something safe to hand to a browser.
 *
 * Kept free of Android types so all of it can be tested on the JVM, and because there is
 * nothing here that needs a Context: a template is just a string, and building the final URL
 * is string substitution plus percent-encoding.
 */
object SearchUrl {

    /** The one placeholder a template may contain, replaced with the encoded query. */
    private const val PLACEHOLDER = "%s"

    private const val MAX_LENGTH = 2048

    /**
     * Stands in for the placeholder while the template is parsed as a URI, since a literal
     * `%s` is not itself legal URI syntax (a `%` must start a two-digit hex escape). Letters
     * only, so it can sit anywhere — scheme, host, path, query or fragment — without changing
     * how that position parses.
     */
    private const val SENTINEL = "searchplaceholdersentinel"

    /** Why a template was rejected, so the settings screen can say something specific. */
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
        /** [insecure] is true for a plain `http` template, which still works but warns. */
        data class Ok(val insecure: Boolean) : Validation
        data class Invalid(val reason: Reason) : Validation
    }

    /**
     * Checked in the order listed on each field/rule below: cheap structural checks first, the
     * URI parse last, so a template that fails early never reaches java.net.URI at all.
     */
    fun validate(template: String): Validation {
        val trimmed = template.trim()
        if (trimmed.isEmpty()) return Validation.Invalid(Reason.BLANK)
        if (trimmed.length > MAX_LENGTH) return Validation.Invalid(Reason.TOO_LONG)
        if (trimmed.any { it.isIsoControl() }) return Validation.Invalid(Reason.CONTROL_CHARACTER)

        val placeholderCount = trimmed.windowed(PLACEHOLDER.length, step = 1)
            .count { it == PLACEHOLDER }
        if (placeholderCount == 0) return Validation.Invalid(Reason.NO_PLACEHOLDER)
        if (placeholderCount > 1) return Validation.Invalid(Reason.MULTIPLE_PLACEHOLDERS)

        // A literal "%s" is not legal URI syntax, so it is swapped for a same-shaped, URI-safe
        // stand-in before parsing, and the stand-in's position is checked against the parsed
        // authority afterwards.
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

        // uri.host/uri.userInfo only recognise an ASCII "server-based" authority — an IDN
        // written in raw Unicode (not punycode) is left as null by both, even though it is a
        // perfectly good host. rawAuthority has no such limit, so userinfo and host are read
        // off it directly instead.
        val rawAuthority = uri.rawAuthority
        if (rawAuthority.isNullOrEmpty()) return Validation.Invalid(Reason.MISSING_HOST)
        if (rawAuthority.contains('@')) return Validation.Invalid(Reason.USERINFO_NOT_ALLOWED)

        // A legitimate search host never needs a percent-escape, and the three parsers that
        // handle this template in turn disagree about what one means: java.net.URI (right here)
        // leaves it raw, Android's Uri.getHost() percent-decodes it, and the browser that
        // finally opens it decodes it and then re-validates — so something like %40 or %2F
        // sitting in the host could name a different site to each of them. Refusing any '%' in
        // the authority closes that gap outright rather than trying to enumerate the escapes
        // that are dangerous. This also catches an IPv6 zone ID (e.g. "[fe80::1%25eth0]"), which
        // is always introduced by a literal "%25" and has no legitimate use in a search
        // template. The placeholder itself was already swapped for a letters-only stand-in
        // above, so this never fires on a %s the user put in the host — that case is still
        // reported as PLACEHOLDER_IN_AUTHORITY below, not this.
        if (rawAuthority.contains('%')) return Validation.Invalid(Reason.PERCENT_ESCAPE_IN_AUTHORITY)

        val host = if (rawAuthority.startsWith("[")) {
            val closingBracket = rawAuthority.indexOf(']')
            if (closingBracket < 0) rawAuthority else rawAuthority.substring(0, closingBracket + 1)
        } else {
            rawAuthority.substringBeforeLast(':')
        }
        if (host.isEmpty()) return Validation.Invalid(Reason.MISSING_HOST)

        // A server-based authority is always exactly "scheme://" + rawAuthority, so its end
        // index in the substituted string is just those two lengths added together — no need
        // to search for it.
        val prefix = "$scheme://"
        if (!substituted.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            return Validation.Invalid(Reason.MALFORMED)
        }
        val authorityEnd = prefix.length + rawAuthority.length
        val sentinelIndex = substituted.indexOf(SENTINEL)
        if (sentinelIndex < authorityEnd) return Validation.Invalid(Reason.PLACEHOLDER_IN_AUTHORITY)

        return Validation.Ok(insecure = insecure)
    }

    /**
     * Null for a blank query or an invalid template, so a caller cannot accidentally launch a
     * browser at half a URL. The query is encoded as its own opaque piece — nothing in it can
     * add a parameter, a fragment or a line break to what the template already specifies.
     */
    fun build(template: String, query: String): String? {
        if (validate(template) !is Validation.Ok) return null
        if (query.isBlank()) return null

        // Intent-filter scheme matching on Android is case-sensitive, so a template validate()
        // accepted, like "HTTPS://...", would otherwise resolve to no activity at all. validate()
        // already guarantees the placeholder sits after the authority, so the scheme is exactly
        // the text before the first colon; only that gets lowercased, host/path/query untouched.
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

    /** RFC 3986 percent-encoding over UTF-8 bytes; a space becomes `%20`, never `+`. */
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
