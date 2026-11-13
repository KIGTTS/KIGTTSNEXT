package com.kigtts.next.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kigtts.next.media.color.*
import com.kigtts.next.ui.cards.camera.PhotoFilter
import com.kigtts.next.ui.cards.camera.PhotoFilters
import com.kigtts.next.ui.cards.camera.applyPhotoFilterBands
import com.kigtts.plugin.api.PolaroidPhotoStyle
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/** Capture owns only the original and a recipe; the small LUT atlas is an ordinary portable resource. */
data class CapturedPhotoStyle(val filter: PhotoFilter = PhotoFilter.ORIGINAL, val strength: Float = 1f,
    val lut: CubeLut? = null, val effects: PhotoEffects = PhotoEffects.NONE) {
    internal fun requiresRendering() = effects.active || filter != PhotoFilter.ORIGINAL && strength > 0f
    fun persist(stickers: StickerRepository): PolaroidPhotoStyle {
        val custom = lut?.takeIf { filter == PhotoFilter.CUSTOM }
        val imageId = custom?.let {
            val atlas = it.atlas()
            val stage = File(stickers.context.cacheDir, "photo-lut-${UUID.randomUUID()}.png")
            try {
                stage.outputStream().use { output -> check(atlas.compress(Bitmap.CompressFormat.PNG, 100, output)) }
                stickers.importResource(stage).id
            } finally { atlas.recycle(); stage.delete() }
        }
        return PolaroidPhotoStyle(filter.name, strength, effects.vignette, effects.grain, effects.aberration, effects.bloom,
            imageId, custom?.size ?: 0, custom?.domainMin?.toList() ?: listOf(0f, 0f, 0f), custom?.domainMax?.toList() ?: listOf(1f, 1f, 1f))
    }
}

internal fun PolaroidPhotoStyle.photoFilter() = PhotoFilter.entries.firstOrNull { it.name == filter } ?: PhotoFilter.ORIGINAL
internal fun PolaroidPhotoStyle.photoEffects() = PhotoEffects(vignette, grain, aberration, bloom)
internal fun PolaroidPhotoStyle.customLut(imageFile: (String) -> File?): CubeLut? = lutImageId?.let { id ->
    val image = requireNotNull(imageFile(id)?.let { BitmapFactory.decodeFile(it.path) }) { "滤镜资源暂时无法读取" }
    try { CubeLut.fromAtlas(image, lutSize, lutMin, lutMax) } finally { image.recycle() }
}
internal fun PolaroidPhotoStyle.hasPhotoTreatment() = photoEffects().active || photoFilter() != PhotoFilter.ORIGINAL && strength > 0f
internal fun com.kigtts.plugin.api.PolaroidCard.withPhotoStyle(style: PolaroidPhotoStyle) = copy(
    imageId = if (photoStyle == null) originalImageId ?: imageId else imageId, photoStyle = style)
internal suspend fun applyCardPhotoStyle(image: Bitmap, style: PolaroidPhotoStyle?, imageFile: (String) -> File?) {
    if (style != null && style.hasPhotoTreatment())
        applyPhotoFilterBands(image, style.photoFilter(), style.strength, style.customLut(imageFile), style.photoEffects())
}
internal fun renderCardPhotoStyle(image: Bitmap, style: PolaroidPhotoStyle?, imageFile: (String) -> File?) {
    runBlocking { applyCardPhotoStyle(image, style, imageFile) }
}

/** A card recipe overrides old motion metadata; legacy baked covers keep their original video treatment. */
internal fun CardMotionClip.withPhotoStyle(style: PolaroidPhotoStyle?, imageFile: (String) -> File?): CardMotionClip =
    if (style == null) this else copy(colorMatrix = PhotoFilters.coefficients(style.photoFilter(), style.strength),
        lut = PhotoFilters.lut(style.photoFilter(), style.customLut(imageFile)), lutStrength = style.strength, effects = style.photoEffects())
