// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashRedactionTest {

    @Test
    fun `private packages are redacted in stack frames and component strings`() {
        val trace = """
            java.lang.IllegalStateException: ComponentInfo{com.example.secret/.HiddenActivity}
                at com.example.secret.HiddenActivity.onCreate(HiddenActivity.kt:12)
                at com.example.secret.worker.Job.run(Job.kt:4)
                at com.example.publicapp.Main.onCreate(Main.kt:8)
                at comXexampleXsecret.NotAPackage.go(NotAPackage.kt:1)
        """.trimIndent()

        assertEquals(
            """
                java.lang.IllegalStateException: ComponentInfo{[private]/.HiddenActivity}
                    at [private].HiddenActivity.onCreate(HiddenActivity.kt:12)
                    at [private].worker.Job.run(Job.kt:4)
                    at com.example.publicapp.Main.onCreate(Main.kt:8)
                    at comXexampleXsecret.NotAPackage.go(NotAPackage.kt:1)
            """.trimIndent(),
            redactCrashTrace(
                trace = trace,
                privatePackages = setOf("com.example.secret"),
                privateLabels = emptySet(),
            ),
        )
    }

    @Test
    fun `overlapping package names prefer the longest private match`() {
        val trace = """
            at com.example.secret.Hidden.run(Hidden.kt:1)
            at com.example.Visible.run(Visible.kt:2)
        """.trimIndent()

        assertEquals(
            """
                at [private].Hidden.run(Hidden.kt:1)
                at [private].Visible.run(Visible.kt:2)
            """.trimIndent(),
            redactCrashTrace(
                trace = trace,
                privatePackages = setOf("com.example", "com.example.secret"),
                privateLabels = emptySet(),
            ),
        )
    }

    /**
     * The dangerous half of the prefix problem: a plain substring replace would turn the
     * innocent `com.example.secretive` into `[private]ive`, which both mangles the report and
     * says a package starting with the private one's name exists.
     */
    @Test
    fun `a public package the private one is a prefix of is left alone`() {
        val trace = """
            at com.example.secret.Hidden.run(Hidden.kt:1)
            at com.example.secretive.Public.run(Public.kt:2)
            ComponentInfo{com.example.secretive/.Main}
            java.lang.IllegalStateException: com.example.secret
        """.trimIndent()

        assertEquals(
            """
                at [private].Hidden.run(Hidden.kt:1)
                at com.example.secretive.Public.run(Public.kt:2)
                ComponentInfo{com.example.secretive/.Main}
                java.lang.IllegalStateException: [private]
            """.trimIndent(),
            redactCrashTrace(
                trace = trace,
                privatePackages = setOf("com.example.secret"),
                privateLabels = emptySet(),
            ),
        )
    }

    @Test
    fun `private labels are redacted as whole case sensitive words`() {
        val trace = "Secret opened Chat, but Secretly and Chatterbox stayed visible."

        assertEquals(
            "[private] opened [private], but Secretly and Chatterbox stayed visible.",
            redactCrashTrace(
                trace = trace,
                privatePackages = emptySet(),
                privateLabels = setOf("Secret", "Chat"),
            ),
        )
    }

    @Test
    fun `labels shorter than three characters are ignored`() {
        val trace = "AI and Go are labels, Calendar is private."

        assertEquals(
            "AI and Go are labels, [private] is private.",
            redactCrashTrace(
                trace = trace,
                privatePackages = emptySet(),
                privateLabels = setOf("AI", "Go", "Calendar"),
            ),
        )
    }

    @Test
    fun `nonzero user suffixes are redacted while user zero is kept`() {
        val trace = """
            com.safe/.Main|u0
            com.secret/.Main|u11
            shortcut:com.secret/open|u003
            shortcut:com.safe/open|u000
        """.trimIndent()

        assertEquals(
            """
                com.safe/.Main|u0
                com.secret/.Main[private]
                shortcut:com.secret/open[private]
                shortcut:com.safe/open|u000
            """.trimIndent(),
            redactCrashTrace(
                trace = trace,
                privatePackages = emptySet(),
                privateLabels = emptySet(),
            ),
        )
    }

    @Test
    fun `a trace with nothing private is unchanged`() {
        val trace = """
            java.lang.IllegalArgumentException: ordinary crash
                at com.example.safe.Main.onCreate(Main.kt:42)
            ComponentInfo{com.example.safe/.MainActivity}|u0
        """.trimIndent()

        assertEquals(
            trace,
            redactCrashTrace(
                trace = trace,
                privatePackages = setOf("com.example.secret"),
                privateLabels = setOf("Clock"),
            ),
        )
    }
}
