// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

/**
 * The strip's non-letter targets.
 *
 * They ride in `letterIndex` exactly like A-Z entries do, so scrubbing, the solo-letter fade
 * and the release placement all reach them without knowing they are not letters. A char rather
 * than a sealed type for the same reason: everything downstream already speaks in chars, and a
 * second kind of target would have to be threaded through every one of those places.
 *
 * They are drawn as icons rather than as the characters themselves — the shield in particular
 * is missing from most system fonts and would come out as a tofu box. Until that lands (round
 * 2 item A) the strip simply prints them, which is why they are real characters and not
 * private-use codepoints: a fallback that prints something recognisable beats one that does
 * not.
 */

/** Favorites: the top of the list, which is the home screen. */
const val GLYPH_FAVORITES = '★'

/** The private space's own section. Never shown when there is no private space to point at. */
const val GLYPH_PRIVATE = '⛨'

/** The launcher's own rows, at the very bottom. */
const val GLYPH_LAUNCHER = '•'
