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
    val names = linkedMapOf("circle" to "圆形", "flower" to "花朵", "cookie" to "十二瓣", "clover" to "四叶", "sun" to "太阳",
        "very-sunny" to "小太阳", "cookie4" to "四瓣", "cookie6" to "六瓣", "cookie7" to "七瓣", "cookie9" to "九瓣",
        "clover8" to "八叶", "burst" to "光芒", "soft-burst" to "柔光", "boom" to "绽放", "soft-boom" to "柔瓣",
        "puffy" to "蓬蓬", "puffy-diamond" to "蓬菱", "heart" to "爱心", "ghost" to "小幽灵", "bun" to "云朵",
        "square" to "方形", "slanted" to "斜方", "arch" to "拱形", "fan" to "扇形", "arrow" to "箭头",
        "semi-circle" to "半圆", "oval" to "椭圆", "pill" to "胶囊", "triangle" to "三角", "diamond" to "菱形",
        "clam-shell" to "贝壳", "pentagon" to "五边", "gem" to "宝石", "pixel-circle" to "像素圆", "pixel-triangle" to "像素角")
    private fun polygon(id: String): RoundedPolygon = when (id) {
        "flower" -> MaterialShapes.Flower; "cookie" -> MaterialShapes.Cookie12Sided
        "clover" -> MaterialShapes.Clover4Leaf; "sun" -> MaterialShapes.Sunny
        "very-sunny" -> MaterialShapes.VerySunny; "cookie4" -> MaterialShapes.Cookie4Sided
        "cookie6" -> MaterialShapes.Cookie6Sided; "cookie7" -> MaterialShapes.Cookie7Sided; "cookie9" -> MaterialShapes.Cookie9Sided
        "clover8" -> MaterialShapes.Clover8Leaf; "burst" -> MaterialShapes.Burst; "soft-burst" -> MaterialShapes.SoftBurst
        "boom" -> MaterialShapes.Boom; "soft-boom" -> MaterialShapes.SoftBoom; "puffy" -> MaterialShapes.Puffy
        "puffy-diamond" -> MaterialShapes.PuffyDiamond; "heart" -> MaterialShapes.Heart; "ghost" -> MaterialShapes.Ghostish
        "bun" -> MaterialShapes.Bun; "square" -> MaterialShapes.Square; "slanted" -> MaterialShapes.Slanted
        "arch" -> MaterialShapes.Arch; "fan" -> MaterialShapes.Fan; "arrow" -> MaterialShapes.Arrow
        "semi-circle" -> MaterialShapes.SemiCircle; "oval" -> MaterialShapes.Oval; "pill" -> MaterialShapes.Pill
        "triangle" -> MaterialShapes.Triangle; "diamond" -> MaterialShapes.Diamond; "clam-shell" -> MaterialShapes.ClamShell
        "pentagon" -> MaterialShapes.Pentagon; "gem" -> MaterialShapes.Gem
        "pixel-circle" -> MaterialShapes.PixelCircle; "pixel-triangle" -> MaterialShapes.PixelTriangle
        else -> MaterialShapes.Circle
    }
    private val paths = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val samples = java.util.concurrent.ConcurrentHashMap<String, FloatArray>()
    fun svgPath(id: String): String = paths.getOrPut(id.takeIf(names::containsKey) ?: "circle") {
        val shape = polygon(id); val bounds = shape.calculateBounds()
        fun x(value: Float) = (value - bounds[0]) / (bounds[2] - bounds[0])
        fun y(value: Float) = (value - bounds[1]) / (bounds[3] - bounds[1])
        fun number(value: Float) = String.format(Locale.ROOT, "%.6f", value)
        buildString { shape.cubics.forEachIndexed { index, c ->
            if (index == 0) append("M${number(x(c.anchor0X))},${number(y(c.anchor0Y))}")
            append("C${number(x(c.control0X))},${number(y(c.control0Y))} ${number(x(c.control1X))},${number(y(c.control1Y))} ${number(x(c.anchor1X))},${number(y(c.anchor1Y))}")
        }; append('Z') }
    }
    internal fun points(id: String): FloatArray = samples.getOrPut(id.takeIf(names::containsKey) ?: "circle") {
        val path = androidx.compose.ui.graphics.vector.PathParser().parsePathString(svgPath(id)).toPath().asAndroidPath()
        val measure = android.graphics.PathMeasure(path, true); val position = FloatArray(2)
        FloatArray(384).also { result -> repeat(192) { i ->
            measure.getPosTan(measure.length * i / 192, position, null)
            result[i * 2] = position[0]; result[i * 2 + 1] = position[1]
        } }
    }
    fun shape(id: String): Shape = object : Shape {
        private val source = androidx.compose.ui.graphics.vector.PathParser().parsePathString(svgPath(id)).toPath()
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val path = Path().apply { addPath(source) }
            path.transform(Matrix().apply { scale(size.width, size.height) })
            return Outline.Generic(path)
        }
    }
}
