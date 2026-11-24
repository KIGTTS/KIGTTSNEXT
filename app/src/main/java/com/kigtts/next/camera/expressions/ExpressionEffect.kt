package com.kigtts.next.camera.expressions

import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RectF
import com.kigtts.next.legacy.checkBackupJsonDepth
import com.kigtts.next.media.UiIcons
import com.kigtts.plugin.api.PluginJson
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

@Serializable
internal enum class ExpressionAnchor { FACE, EYES, LEFT_EYE, RIGHT_EYE, MOUTH, NOSE, FOREHEAD }

@Serializable
internal data class ExpressionLayer(
    val svg: String,
    val anchor: ExpressionAnchor = ExpressionAnchor.FACE,
    val scale: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
)

@Serializable
internal data class ExpressionEffect(val id: String, val name: String, val layers: List<ExpressionLayer>)

@Serializable
internal data class ExpressionPack(val version: Int = 1, val effects: List<ExpressionEffect>) {
    fun validate(): ExpressionPack {
        require(version == 1 && effects.size in 1..24 && effects.map { it.id }.distinct().size == effects.size)
        effects.forEach { effect ->
            require(effect.id.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")))
            require(effect.name.isNotBlank() && effect.name.length <= 80 && effect.layers.size in 1..8)
            effect.layers.forEach { layer ->
                require(layer.svg.toByteArray(Charsets.UTF_8).size in 1..MAX_SVG &&
                    layer.scale in .15f..3f && layer.x in -1f..1f && layer.y in -1f..1f &&
                    layer.rotation in -180f..180f && layer.opacity in .1f..1f)
            }
        }
        return this
    }

    companion object {
        const val MAX_SVG = 180 * 1024
        const val MAX_PACK = 3 * 1024 * 1024
        const val MIME = "application/vnd.kigtts.expression+json"
        fun read(text: String): ExpressionPack {
            require(text.toByteArray(Charsets.UTF_8).size in 1..MAX_PACK)
            checkBackupJsonDepth(text)
            return PluginJson.decodeFromString<ExpressionPack>(text.removePrefix("\uFEFF")).validate()
        }
    }
}

internal data class ExpressionPoint(val x: Float, val y: Float)

internal data class ExpressionFace(
    val id: Long, val bounds: RectF, val opacity: Float, val confidence: Float,
    val leftEye: ExpressionPoint, val rightEye: ExpressionPoint, val nose: ExpressionPoint,
    val leftMouth: ExpressionPoint, val rightMouth: ExpressionPoint,
) {
    val eyeDistance get() = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)
    private fun center(a: ExpressionPoint, b: ExpressionPoint) = ExpressionPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val eyeCenter get() = center(leftEye, rightEye)
    val mouthCenter get() = center(leftMouth, rightMouth)
    val roll get() = (atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x) * 180 / PI).toFloat()

    companion object {
        val SAMPLE = ExpressionFace(0, RectF(80f, 36f, 240f, 284f), 1f, 1f,
            ExpressionPoint(123f, 125f), ExpressionPoint(197f, 125f), ExpressionPoint(160f, 170f),
            ExpressionPoint(134f, 216f), ExpressionPoint(186f, 216f))
    }
}

internal class CompiledExpression(val effect: ExpressionEffect, artwork: List<Picture>) {
    private val layers = effect.layers.zip(artwork)

    fun draw(canvas: Canvas, faces: List<ExpressionFace>) {
        faces.forEach { face ->
            val width = face.bounds.width()
            if (width <= 0f || face.eyeDistance <= 1f || face.opacity <= 0f) return@forEach
            layers.forEach { (layer, picture) ->
                val eyes = face.eyeCenter
                val point = when (layer.anchor) {
                    ExpressionAnchor.FACE -> ExpressionPoint(face.bounds.centerX(), face.bounds.centerY())
                    ExpressionAnchor.EYES -> eyes
                    ExpressionAnchor.LEFT_EYE -> face.leftEye
                    ExpressionAnchor.RIGHT_EYE -> face.rightEye
                    ExpressionAnchor.MOUTH -> face.mouthCenter
                    ExpressionAnchor.NOSE -> face.nose
                    ExpressionAnchor.FOREHEAD -> ExpressionPoint(eyes.x, eyes.y - face.eyeDistance * .65f)
                }
                val base = when (layer.anchor) {
                    ExpressionAnchor.FACE, ExpressionAnchor.FOREHEAD -> width
                    ExpressionAnchor.EYES -> face.eyeDistance * 2f
                    ExpressionAnchor.LEFT_EYE, ExpressionAnchor.RIGHT_EYE, ExpressionAnchor.NOSE -> face.eyeDistance * .72f
                    ExpressionAnchor.MOUTH -> face.eyeDistance * 1.4f
                }
                val nominal = base * layer.scale
                val save = canvas.save()
                canvas.translate(point.x, point.y)
                canvas.rotate(face.roll)
                canvas.translate(width * layer.x, width * layer.y)
                canvas.rotate(layer.rotation)
                // 1.1.8's ink sheets have the eye line on row 99 of the original 320px art.
                val inset = if (layer.anchor == ExpressionAnchor.EYES) 99f / 320f else .5f
                val target = RectF(-nominal / 2f, -nominal * inset, nominal / 2f, nominal * (1f - inset))
                val alpha = (255 * layer.opacity * face.opacity).toInt().coerceIn(0, 255)
                if (alpha < 255) {
                    val opacity = canvas.saveLayerAlpha(target, alpha)
                    canvas.drawPicture(picture, target)
                    canvas.restoreToCount(opacity)
                } else canvas.drawPicture(picture, target)
                canvas.restoreToCount(save)
            }
        }
    }

    companion object {
        fun compile(effect: ExpressionEffect): CompiledExpression {
            ExpressionPack(effects = listOf(effect)).validate()
            return CompiledExpression(effect, effect.layers.map { UiIcons.renderSvgPicture(it.svg, 320) })
        }
    }
}
