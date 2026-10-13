package com.kigtts.next.ui.cards.camera

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

/** Verify the live GLSL uniform contract against the separately saved JPEG's encoded RGB math. */
class CameraPreviewShaderTest {
    @Test fun everyFilterAndStrengthMatchesTheSavedPhotographIncludingByteDomainOffsets() {
        val samples = listOf(0xffc93472.toInt(), 0xff08c41b.toInt(), 0xff18f9f2.toInt(), -1, 0xff000000.toInt())
        for (filter in PhotoFilter.entries) for (strength in listOf(0f, .35f, 1f)) {
            val values = PhotoFilters.coefficients(filter, strength); val uniforms = cameraPreviewUniforms(values)
            for (color in samples) {
                val source = floatArrayOf((color ushr 16 and 255) / 255f, (color ushr 8 and 255) / 255f,
                    (color and 255) / 255f, 1f)
                fun channel(row: Int): Int = ((source.indices.sumOf { (source[it] * uniforms.rows[row * 4 + it]).toDouble() }
                    + uniforms.bias[row]) * 255).roundToInt().coerceIn(0, 255)
                val actual = (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
                val expected = PhotoFilters.apply(color, values)
                for (shift in listOf(0, 8, 16, 24)) assertEquals((expected ushr shift and 255).toDouble(),
                    (actual ushr shift and 255).toDouble(), 1.0)
            }
        }
    }
    @Test fun cameraAnimationChangesUniformsWithoutRetainingTheCallersMutableArray() {
        val colors = CameraPreviewColors(); val first = colors.snapshot()
        val selected = PhotoFilters.coefficients(PhotoFilter.WARM)
        colors.update(selected); selected.fill(0f)
        val warm = colors.snapshot(); assertNotSame(first, warm)
        assertEquals(1.04f, warm.rows[0], 0f); assertEquals(5f / 255f, warm.bias[0], 0f)
        assertEquals(1f, first.rows[0], 0f)
        assertThrows(IllegalArgumentException::class.java) { colors.update(FloatArray(3)) }
        assertThrows(IllegalArgumentException::class.java) { colors.update(FloatArray(20) { Float.NaN }) }
        assertSame(warm, colors.snapshot())
    }
}
