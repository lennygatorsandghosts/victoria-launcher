// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import dev.victorialauncher.R

/**
 * What the padlock row says and which picture it wears, decided from the one thing either
 * depends on: whether the space is open right now.
 *
 * Pulled out of [AppRepository.privateSpaceRow] so both halves of the decision can be tested
 * on the JVM. A PrivateSpace cannot be built in a unit test — Locked and Unlocked each hold a
 * UserHandle, which the stub android.jar will not construct — so this is told the answer
 * rather than asked to read it, the same way [shouldListProfile] is.
 *
 * Only [PrivateSpace.Unlocked] is open. Locked and Uncertain both read as shut, because
 * Uncertain is treated as locked everywhere else and a row offering to lock a space that is
 * already locked is a row that does nothing.
 */
object PrivateSpaceRow {

    /** Also what keeps the two padlocks apart in the icon cache, which is keyed by the row. */
    fun className(unlocked: Boolean): String =
        if (unlocked) PRIVATE_SPACE_UNLOCKED_CLASS else PRIVATE_SPACE_LOCKED_CLASS

    /**
     * The row says what pressing it will do, not what it is: the section heading above it is
     * already called "Private space", and two rows reading the same thing say nothing.
     */
    fun labelRes(unlocked: Boolean): Int =
        if (unlocked) R.string.private_space_lock else R.string.private_space_unlock
}
