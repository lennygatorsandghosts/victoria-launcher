// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

/** What anything belonging to the private space is replaced by. */
private const val REDACTED = "[private]"

/** Labels shorter than this match too much ordinary text to be worth replacing. */
private const val MIN_LABEL_LENGTH = 3

/**
 * A profile suffix on a stored key or a Compose key: `|u` and the profile's serial. Zero is
 * the main profile, which every old key assumed and which says nothing about a second one.
 */
private val USER_SUFFIX = Regex("""\|u(\d+)""")

/**
 * Takes a crash trace apart far enough that copying it cannot hand over what is inside the
 * private space.
 *
 * A launcher's trace is the one piece of text in this app that is written by the framework
 * rather than by us, so what ends up in it is not ours to predict: a Compose duplicate-key
 * message carries `pkg/cls|u<serial>`, a binder failure carries a `ComponentInfo{…}`, and an
 * app's own exception carries whatever it felt like putting in the message. Rather than guess
 * which of those can happen, everything that names the space goes.
 *
 * Three rules, and all three are deliberately blunt:
 *  - a private **package**, wherever it appears — on its own, as the prefix of a class name,
 *    or inside `ComponentInfo{pkg/…}`. Matched **literally**, never as a pattern: the dots in
 *    a package name are regex wildcards, and `com.example.secret` as a pattern also matches
 *    `comXexampleXsecret`, which is a different string and not a leak. Longest package first,
 *    so a package that is a prefix of another private one does not redact half of it.
 *  - a private **label**, as a whole word. Two characters match too much ordinary English
 *    ("Go", "AI") to be worth it, so those are left; the package rule still covers them.
 *  - a **`|u<serial>` suffix** whose serial is not zero. This one needs no list: it is the
 *    shape of a key belonging to some profile other than the main one, and there is no reading
 *    of that which is safe to put on a clipboard.
 *
 * The replacement is never the empty string: a reader of the report has to be able to see that
 * something was taken out, or a trace with a hole in it looks like a trace that was truncated.
 *
 * Over-redaction is the deliberate direction of every edge case here. A bug report with an
 * extra `[private]` in it is a worse bug report; a bug report naming an app the owner hid is a
 * different kind of problem entirely.
 *
 * Pure, and takes the two sets rather than reading them, so it can be tested without a device:
 * see `CrashRedactionTest`. A trace holding nothing private comes back unchanged.
 */
fun redactCrashTrace(
    trace: String,
    privatePackages: Set<String>,
    privateLabels: Set<String>,
): String {
    var out = trace

    // Longest first: with both com.example and com.example.secret private, replacing the
    // short one first would leave "[private].secret" and name what was meant to go.
    privatePackages
        .filter { it.isNotBlank() }
        .sortedByDescending { it.length }
        .forEach { pkg -> out = replaceWholeTokens(out, pkg) }

    privateLabels
        .filter { it.isNotBlank() && it.length >= MIN_LABEL_LENGTH }
        .sortedByDescending { it.length }
        .forEach { label -> out = replaceWholeTokens(out, label) }

    out = USER_SUFFIX.replace(out) { match ->
        // trimStart rather than toLong: a serial long enough to overflow is still not zero,
        // and throwing while redacting would leave the un-redacted text to be copied.
        if (match.groupValues[1].trimStart('0').isEmpty()) match.value else REDACTED
    }

    return out
}

/**
 * Every literal occurrence of [token] that is not part of a longer name, replaced.
 *
 * "Part of a longer name" is the whole point: `com.example.secret` must not match inside
 * `com.example.secretive`, which belongs to somebody else, and the label "Chat" must not match
 * inside "Chatterbox". So a match counts only when the characters on both sides of it are not
 * ones a name is made of. A dot is not one of those, which is what lets the package rule reach
 * `com.example.secret.HiddenActivity` and `ComponentInfo{com.example.secret/.Main}`.
 *
 * Scanning by [String.indexOf] rather than by pattern is what keeps the match literal.
 */
private fun replaceWholeTokens(text: String, token: String): String {
    var from = 0
    var found = text.indexOf(token, from)
    if (found < 0) return text

    val out = StringBuilder(text.length)
    while (found >= 0) {
        val end = found + token.length
        val boundedBefore = found == 0 || !isNameChar(text[found - 1])
        val boundedAfter = end == text.length || !isNameChar(text[end])
        out.append(text, from, found)
        out.append(if (boundedBefore && boundedAfter) REDACTED else text.substring(found, end))
        from = end
        found = text.indexOf(token, from)
    }
    out.append(text, from, text.length)
    return out.toString()
}

/** A character a package, class or label name runs through without a break. */
private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

/**
 * What [redactCrashTrace] needs to know about the private space: the packages inside it and
 * the names they go by on screen. Produced by `AppRepository.privateAppNames`.
 */
data class PrivateAppNames(val packages: Set<String>, val labels: Set<String>)
