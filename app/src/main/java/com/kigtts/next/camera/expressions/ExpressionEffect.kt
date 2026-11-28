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
    val hideUnderFringe: Boolean = false,
    val blurBase: Boolean = false,
    val pivotY: Float? = null,
    val bitmapWebp: String? = null,
    val flipX: Boolean = false,
)

@Serializable
internal data class ExpressionEffect(val id: String, val name: String, val layers: List<ExpressionLayer>,
    val blurFace: Boolean? = null) {
    // Older packs stored blur on individual layers. An explicit whole-sticker choice takes precedence.
    val blurFaceEnabled get() = blurFace ?: layers.any { it.blurBase }
}

@Serializable
internal data class ExpressionPack(val version: Int = 1, val effects: List<ExpressionEffect>) {
    fun validate(): ExpressionPack {
        require(version == 1 && effects.size in 1..24 && effects.map { it.id }.distinct().size == effects.size)
        effects.forEach { effect ->
            require(effect.id.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")))
            require(effect.name.isNotBlank() && effect.name.length <= 80 && effect.layers.size in 1..8)
            effect.layers.forEach { layer ->
                require(layer.svg.toByteArray(Charsets.UTF_8).size in 1..MAX_SVG &&
                    (layer.bitmapWebp == null || layer.bitmapWebp.length in 16..MAX_SVG) &&
                    layer.scale in .15f..3f && layer.x in -1f..1f && layer.y in -1f..1f &&
                    layer.rotation in -180f..180f && layer.opacity in .1f..1f && (layer.pivotY == null || layer.pivotY in 0f..1f))
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
    val surface: ExpressionFaceSurface? = null,
) {
    val eyeDistance get() = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)
    private fun center(a: ExpressionPoint, b: ExpressionPoint) = ExpressionPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val eyeCenter get() = center(leftEye, rightEye)
    val mouthCenter get() = center(leftMouth, rightMouth)
    val roll get() = (atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x) * 180 / PI).toFloat()

    companion object {
        val SAMPLE = ExpressionFace(0, RectF(80f, 36f, 240f, 284f), 1f, 1f,
            ExpressionPoint(123f, 125f), ExpressionPoint(197f, 125f), ExpressionPoint(160f, 170f),
            ExpressionPoint(134f, 216f), ExpressionPoint(186f, 216f), ExpressionFaceSurface.SAMPLE)
    }
}

internal class CompiledExpression(val effect: ExpressionEffect, artwork: List<Picture>) {
    private val layers = effect.layers.zip(artwork)
    private val blurFace = effect.blurFaceEnabled
    val samplesSurface = blurFace || effect.layers.any { it.hideUnderFringe }

    fun draw(canvas: Canvas, faces: List<ExpressionFace>) {
        faces.forEach { face ->
            if (face.bounds.width() <= 0f || face.eyeDistance <= 1f || face.opacity <= 0f) return@forEach
            if (blurFace) face.surface?.drawBase(canvas, (255 * face.opacity).toInt().coerceIn(0, 255))
            layers.forEach { (layer, picture) ->
                val placement = layer.placement(face)
                val save = canvas.save()
                if (layer.hideUnderFringe) face.surface?.clip(canvas)
                val alpha = (255 * layer.opacity * face.opacity).toInt().coerceIn(0, 255)
                canvas.translate(placement.anchor.x, placement.anchor.y)
                canvas.rotate(placement.faceRoll)
                canvas.translate(placement.offset.x, placement.offset.y)
                canvas.rotate(placement.rotation)
                if (layer.flipX) canvas.scale(-1f, 1f)
                val target = placement.bounds()
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
            return CompiledExpression(effect, effect.layers.map { renderExpressionArtwork(it.svg, it.bitmapWebp) })
        }
    }
}

internal fun renderExpressionArtwork(svg: String, bitmap: String?): Picture =
    bitmap?.let(::expressionBitmapPicture) ?: UiIcons.renderSvgPicture(svg, 320)
