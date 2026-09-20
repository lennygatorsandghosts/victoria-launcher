// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** Observe live height during measurement so a drag need not wait for recomposition.
 * Like Modifier.height, the requested height still obeys the parent's constraints. */
internal fun Modifier.layoutHeight(heightDp: () -> Int): Modifier = layout { measurable, constraints ->
    val height = constraints.constrainHeight(heightDp().dp.roundToPx())
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}
