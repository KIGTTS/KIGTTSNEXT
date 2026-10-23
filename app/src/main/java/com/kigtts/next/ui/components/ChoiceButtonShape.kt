@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.foundation.shape.*
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*

/** Use native M3E topology while a single owner animates selection and press; no keyed native morph. */
internal class ChoiceButtonShape(private val defaults: ToggleButtonShapes, private val selected: Float,
    private val pressed: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val base = defaults.shape as CornerBasedShape; val checked = defaults.checkedShape as CornerBasedShape
        val down = defaults.pressedShape as CornerBasedShape
        fun corner(a: CornerSize, b: CornerSize, c: CornerSize): CornerSize {
            val rest = a.toPx(size, density) + (b.toPx(size, density) - a.toPx(size, density)) * selected.coerceIn(0f, 1f)
            return CornerSize(rest + (c.toPx(size, density) - rest) * pressed.coerceIn(0f, 1f))
        }
        return RoundedCornerShape(corner(base.topStart, checked.topStart, down.topStart), corner(base.topEnd, checked.topEnd, down.topEnd),
            corner(base.bottomEnd, checked.bottomEnd, down.bottomEnd), corner(base.bottomStart, checked.bottomStart, down.bottomStart))
            .createOutline(size, layoutDirection, density)
    }
}
