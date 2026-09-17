// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import androidx.datastore.preferences.core.MutablePreferences

internal object NiagaraPreset {
    const val ICON_SIZE_DP = 26
    const val LABEL_SIZE_SP = 18
    const val ITEM_SPACING_DP = 0
    val ALIGNMENT = HomeAlignment.LEFT
    val APPLIST_ALIGNMENT = HomeAlignment.LEFT
    val AZ_STRIP_VISIBILITY = AzStripVisibility.ALWAYS

    val values: Map<String, Any> = mapOf(
        Prefs.Keys.ICON_SIZE_DP.name to ICON_SIZE_DP,
        Prefs.Keys.LABEL_SIZE_SP.name to LABEL_SIZE_SP,
        Prefs.Keys.ITEM_SPACING_DP.name to ITEM_SPACING_DP,
        Prefs.Keys.ALIGNMENT.name to ALIGNMENT.name,
        Prefs.Keys.APPLIST_ALIGNMENT.name to APPLIST_ALIGNMENT.name,
        Prefs.Keys.AZ_STRIP_VISIBILITY.name to AZ_STRIP_VISIBILITY.name,
        Prefs.Keys.ALWAYS_SHOW_AZ.name to true,
    )
}

internal fun MutablePreferences.writeNiagaraPresetValues() {
    this[Prefs.Keys.ICON_SIZE_DP] = NiagaraPreset.ICON_SIZE_DP
    this[Prefs.Keys.LABEL_SIZE_SP] = NiagaraPreset.LABEL_SIZE_SP
    this[Prefs.Keys.ITEM_SPACING_DP] = NiagaraPreset.ITEM_SPACING_DP
    this[Prefs.Keys.ALIGNMENT] = NiagaraPreset.ALIGNMENT.name
    this[Prefs.Keys.APPLIST_ALIGNMENT] = NiagaraPreset.APPLIST_ALIGNMENT.name
    this[Prefs.Keys.AZ_STRIP_VISIBILITY] = NiagaraPreset.AZ_STRIP_VISIBILITY.name
    this[Prefs.Keys.ALWAYS_SHOW_AZ] = true
}

suspend fun Prefs.applyNiagaraPreset() {
    editPreferences { writeNiagaraPresetValues() }
}

suspend fun Prefs.applyNiagaraOffer() {
    editPreferences {
        writeNiagaraPresetValues()
        this[Prefs.Keys.NIAGARA_OFFER_SEEN] = true
    }
}

/**
 * Offered once to anyone whose store predates the Niagara defaults (marker 0 = installed before
 * the marker scheme, 1 = a fresh install of an earlier build — Leonard's phone, which was a
 * fresh Vicky+ install that then imported his settings). Marker 2 already has the defaults.
 * Null means the marker has not been read yet, so nothing is decided.
 */
internal fun shouldShowNiagaraOffer(layoutDefaultsVersion: Int?, niagaraOfferSeen: Boolean): Boolean =
    layoutDefaultsVersion != null && layoutDefaultsVersion < 2 && !niagaraOfferSeen
