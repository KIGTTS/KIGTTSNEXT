@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.identity

import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.graphics.shapes.RoundedPolygon
import java.util.Locale

/** Unit-square SVG cubics are the common contract for Compose, image export and web clip paths. */
object AvatarFrames {
    val names = linkedMapOf("circle" to "圆形", "flower" to "花朵", "cookie" to "饼干", "clover" to "四叶", "sun" to "太阳")
    private fun polygon(id: String): RoundedPolygon = when (id) {
        "flower" -> MaterialShapes.Flower; "cookie" -> MaterialShapes.Cookie12Sided
        "clover" -> MaterialShapes.Clover4Leaf; "sun" -> MaterialShapes.Sunny
        else -> MaterialShapes.Circle
    }
    fun svgPath(id: String): String {
        val shape = polygon(id); val bounds = shape.calculateBounds()
        fun x(value: Float) = (value - bounds[0]) / (bounds[2] - bounds[0])
        fun y(value: Float) = (value - bounds[1]) / (bounds[3] - bounds[1])
        fun number(value: Float) = String.format(Locale.ROOT, "%.6f", value)
        return buildString { shape.cubics.forEachIndexed { index, c ->
            if (index == 0) append("M${number(x(c.anchor0X))},${number(y(c.anchor0Y))}")
            append("C${number(x(c.control0X))},${number(y(c.control0Y))} ${number(x(c.control1X))},${number(y(c.control1Y))} ${number(x(c.anchor1X))},${number(y(c.anchor1Y))}")
        }; append('Z') }
    }
    fun shape(id: String): Shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val path = androidx.compose.ui.graphics.vector.PathParser().parsePathString(svgPath(id)).toPath()
            path.transform(Matrix().apply { scale(size.width, size.height) })
            return Outline.Generic(path)
        }
    }
}
