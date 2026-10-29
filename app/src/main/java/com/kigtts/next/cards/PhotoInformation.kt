package com.kigtts.next.cards

import com.kigtts.plugin.api.CardLayer
import com.kigtts.plugin.api.CardLayerKind
import java.util.Locale

private val legacyWeatherCaption = Regex("(?:晴|少云|阴|雾|雨|雪|雷雨) -?\\d+°C · 模型估计")
/** Only remove the old generated suffix; preserve manually written captions. */
internal fun photoWeatherCaption(text: String): String =
    if (legacyWeatherCaption.matches(text)) text.removeSuffix(" · 模型估计") else text

/** Stable per-card IDs make each capture fact a toggle, including after sharing a design. */
internal fun PhotoTextField.layerId() = "photo-info." + name.lowercase(Locale.ROOT)
internal fun CardLayer.photoField(): PhotoTextField? = if (kind == CardLayerKind.PHOTO_INFO)
    PhotoTextField.entries.firstOrNull { id == it.layerId() } else null
internal fun photoInformationLayer(field: PhotoTextField, text: String) =
    CardLayer(id = field.layerId(), kind = CardLayerKind.PHOTO_INFO, text = text)
internal val PhotoTextField.symbol: String get() = when (this) {
    PhotoTextField.DATE -> "calendar_month"
    PhotoTextField.TIME -> "schedule"
    PhotoTextField.DATE_TIME -> "event"
    PhotoTextField.LOCATION -> "location_on"
    PhotoTextField.WEATHER -> "cloud"
}
