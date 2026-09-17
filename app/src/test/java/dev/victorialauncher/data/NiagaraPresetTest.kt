// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NiagaraPresetTest {

    @Test
    fun `preset values are all allowed for import`() {
        NiagaraPreset.values.forEach { (name, value) ->
            val known = Prefs.importAllowList[name]
            assertTrue("missing from import allow-list: $name", known != null)
            val valid = when (known!!.type) {
                ExpectedType.BOOLEAN -> value is Boolean
                ExpectedType.INT -> value is Int && when (val range = known.range) {
                    null -> true
                    is NumericRange.OfInt -> value in range.min..range.max
                    is NumericRange.OfFloat -> false
                }
                ExpectedType.LONG -> value is Long
                ExpectedType.FLOAT -> value is Float && when (val range = known.range) {
                    null -> true
                    is NumericRange.OfFloat -> value in range.min..range.max
                    is NumericRange.OfInt -> false
                }
                ExpectedType.STRING -> value is String
                ExpectedType.STRING_SET -> value is Set<*> && value.all { it is String }
            }
            assertTrue("preset value is not valid for $name: $value", valid)
        }
    }

    @Test
    fun `preset does not touch user content or vbutton keys`() {
        val protected = setOf(
            Prefs.Keys.FAVORITES.name,
            Prefs.Keys.FOLDERS.name,
            Prefs.Keys.WIDGET_ID.name,
            Prefs.Keys.WIDGET_IDS.name,
            Prefs.Keys.HIDDEN_APPS.name,
            Prefs.Keys.NAME_OVERRIDES.name,
            Prefs.Keys.ICON_OVERRIDES.name,
            Prefs.Keys.SEARCH_URL_TEMPLATE.name,
        )
        val touched = NiagaraPreset.values.keys
        assertTrue((touched intersect protected).isEmpty())
        assertFalse(touched.any { it.startsWith("vbutton_") })
    }

    @Test
    fun `niagara offer show decision truth table`() {
        assertFalse(shouldShowNiagaraOffer(layoutDefaultsVersion = null, niagaraOfferSeen = false))
        assertFalse(shouldShowNiagaraOffer(layoutDefaultsVersion = null, niagaraOfferSeen = true))
        assertTrue(shouldShowNiagaraOffer(layoutDefaultsVersion = 0, niagaraOfferSeen = false))
        assertFalse(shouldShowNiagaraOffer(layoutDefaultsVersion = 0, niagaraOfferSeen = true))
        assertTrue(shouldShowNiagaraOffer(layoutDefaultsVersion = 1, niagaraOfferSeen = false))
        assertFalse(shouldShowNiagaraOffer(layoutDefaultsVersion = 1, niagaraOfferSeen = true))
        assertFalse(shouldShowNiagaraOffer(layoutDefaultsVersion = 2, niagaraOfferSeen = false))
        assertFalse(shouldShowNiagaraOffer(layoutDefaultsVersion = 2, niagaraOfferSeen = true))
    }
}
