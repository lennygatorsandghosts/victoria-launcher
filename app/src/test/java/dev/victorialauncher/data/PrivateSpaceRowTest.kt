// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import dev.victorialauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The padlock row now says what pressing it does rather than what it is, because the heading
 * above it is already called "Private space". Getting this backwards offers to lock a space
 * that is already locked, which does nothing and reads as broken.
 */
class PrivateSpaceRowTest {

    @Test
    fun `an open space offers to close it`() {
        assertEquals(R.string.private_space_lock, PrivateSpaceRow.labelRes(unlocked = true))
        assertEquals(PRIVATE_SPACE_UNLOCKED_CLASS, PrivateSpaceRow.className(unlocked = true))
    }

    @Test
    fun `anything else offers the way in`() {
        // Locked and Uncertain both arrive here as false: Uncertain is treated as locked
        // everywhere else, and a space that will not describe itself has to keep its way back
        // in or there is no gesture left to reach it with.
        assertEquals(R.string.private_space_unlock, PrivateSpaceRow.labelRes(unlocked = false))
        assertEquals(PRIVATE_SPACE_LOCKED_CLASS, PrivateSpaceRow.className(unlocked = false))
    }
}
