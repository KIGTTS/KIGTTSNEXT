package com.kigtts.next.cards

import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.api.CardLayer
import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidPhotoStyle
import kotlinx.serialization.Serializable

/** Only portable edits go in the file; local card IDs, asset paths and account information do not. */
@Serializable
internal data class EmbeddedCameraEdit(val version: Int = 1, val frame: String,
    val style: PolaroidPhotoStyle? = null, val takenAt: Long? = null,
    val layers: List<CardLayer> = emptyList(), val flattened: Boolean = false,
    val watermarkLocation: String? = null, val watermarkWeather: String? = null) {
    fun apply(card: PolaroidCard): PolaroidCard {
        if (version != 1 || frame.length > 96 || PhotoFrameSpec.from(frame) == null) return card
        val filter = style?.takeIf { it.lutImageId == null && runCatching { validateCardPhotoStyle(it, emptySet()) }.isSuccess }
        return card.copy(frameStyleId = frame, photoStyle = filter, layers = layers)
    }

    companion object {
        fun encode(card: PolaroidCard, takenAt: Long?): String = encode(card, FrozenPhotoFacts(capturedAt = takenAt))

        fun encode(card: PolaroidCard, facts: FrozenPhotoFacts): String {
            val watermark = PhotoFrameSpec.from(card.frameStyleId)?.watermark
            val edit = EmbeddedCameraEdit(frame = card.frameStyleId, style = card.photoStyle?.takeUnless { it.lutImageId != null },
                takenAt = facts.capturedAt,
                watermarkLocation = facts.location?.takeIf { watermark?.showLocation == true }?.let(::cleanWatermarkFact),
                watermarkWeather = facts.weather?.takeIf { watermark?.showWeather == true }?.let(::photoWeatherCaption)?.let(::cleanWatermarkFact),
                layers = card.layers.takeIf { it.size <= 12 && it.all(::portable) }.orEmpty())
            val serialized = PluginJson.encodeToString(edit)
            val flatten = card.photoStyle?.lutImageId != null || edit.layers.size != card.layers.size ||
                card.title.isNotBlank() || card.description.isNotBlank() || card.photoX != .5 || card.photoY != .5 ||
                card.photoZoom != 1.0 || card.imageId != card.originalImageId || serialized.toByteArray(Charsets.UTF_8).size > 16000
            return if (flatten) PluginJson.encodeToString(edit.copy(style = null, layers = emptyList(), flattened = true)) else serialized
        }
        fun decode(value: String): EmbeddedCameraEdit? = runCatching { PluginJson.decodeFromString<EmbeddedCameraEdit>(value) }
            .getOrNull()?.takeIf { it.version == 1 && it.layers.size <= 12 &&
                listOfNotNull(it.watermarkLocation, it.watermarkWeather).all { fact -> fact.length <= 240 } &&
                it.layers.all(::portable) && it.layers.map(CardLayer::id).distinct().size == it.layers.size }
        private fun portable(layer: CardLayer) = layer.imageId == null && layer.text.length <= 512 &&
            layer.kind != com.kigtts.plugin.api.CardLayerKind.IMAGE && layer.id.length in 1..96 &&
            layer.x in 0.0..1.0 && layer.y in 0.0..1.0 && layer.width in .01..2.0 &&
            layer.rotation in -36000.0..36000.0 && layer.color in 0..0xFFFFFFFFL
    }
}
