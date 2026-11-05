package com.kigtts.next.cards

import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidFrameStyle
import com.kigtts.plugin.api.PolaroidGeometry

/** Durable, self-contained geometry; older documents keep their original paper geometry. */
internal data class PhotoFrameSpec(val width: Int, val height: Int, val style: String = "none", val crop: PhotoCrop? = null) {
    val id get() = "photo.${width}x$height.$style" + (crop?.let { ".c-${it.encode()}" } ?: "")
    fun geometry(): PolaroidGeometry {
        val w = 100.0
        val h = w * height / width
        val edge = if (style == "none") 0.0 else minOf(w, h) * .025
        val strip = if (style == "none") 0.0 else minOf(w, h) * .12
        return PolaroidGeometry(w + edge * 2, h + edge + strip, edge, edge, w, h,
            edge, h + edge + strip * .20, w, minOf(w, h) * .035,
            h + edge + strip * .61, minOf(w, h) * .022, true)
    }
    companion object {
        // Geometry travels with the existing frame key in backups and shares, including on older SDKs.
        private val pattern = Regex("photo\\.([0-9]{1,5})x([0-9]{1,5})\\.([a-z][a-z0-9.-]{0,60}?)(?:\\.c-([0-9a-z]{12}))?")
        fun from(id: String): PhotoFrameSpec? {
            val match = pattern.matchEntire(id) ?: return null
            val w = match.groupValues[1].toInt(); val h = match.groupValues[2].toInt()
            return if (w in 1..32768 && h in 1..32768 && w.toDouble() / h in .05..20.0)
                PhotoFrameSpec(w, h, match.groupValues[3], match.groups[4]?.value?.let { PhotoCrop.decode(it) ?: return null }) else null
        }
    }
}

internal fun photoFrame(card: PolaroidCard, frames: List<PolaroidFrameStyle>): PolaroidFrameStyle {
    val spec = PhotoFrameSpec.from(card.frameStyleId)
    val frame = frames.firstOrNull { it.id == (spec?.style ?: card.frameStyleId) } ?: PolaroidFrames.defaults.first()
    return when {
        spec?.style == "none" -> frame.copy(id = card.frameStyleId, borderWidth = 0.0, photoCornerRadius = 0.0, overlayImageId = null)
        spec != null -> frame.copy(borderWidth = 0.0, photoCornerRadius = 0.0)
        else -> frame
    }
}
