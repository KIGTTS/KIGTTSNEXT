package com.kigtts.next.cards.generators

import com.kigtts.next.cards.FrozenPhotoFacts
import com.kigtts.next.cards.PhotoTextField
import com.kigtts.plugin.api.StickerParameterKind
import com.kigtts.plugin.api.VectorStickerGenerator
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

/** Photo facts are copied when placing a sticker. Later parameter edits remain local to that instance. */
internal enum class PhotoStickerField(val captureField: PhotoTextField?) {
    DATE(PhotoTextField.DATE), TIME(PhotoTextField.TIME), DATE_TIME(PhotoTextField.DATE_TIME),
    YEAR(PhotoTextField.DATE), MONTH(PhotoTextField.DATE), DAY(PhotoTextField.DATE),
    MONTH_EN(PhotoTextField.DATE), WEEKDAY(PhotoTextField.DATE), WEEKDAY_EN(PhotoTextField.DATE),
    LOCATION(PhotoTextField.LOCATION), WEATHER(PhotoTextField.WEATHER);

    fun text(facts: FrozenPhotoFacts): String {
        if (this == LOCATION) return facts.text(PhotoTextField.LOCATION)
        if (this == WEATHER) return facts.text(PhotoTextField.WEATHER)
        val instant = facts.capturedAt ?: return if (this == WEEKDAY_EN || this == MONTH_EN) "Unknown" else "未知"
        val date = Instant.ofEpochMilli(instant).atZone(runCatching { ZoneId.of(facts.zone) }.getOrDefault(ZoneId.systemDefault()))
        val pattern = when (this) {
            DATE -> "yyyy.MM.dd"; TIME -> "HH:mm"; DATE_TIME -> "yyyy.MM.dd HH:mm"
            YEAR -> "yyyy"; MONTH -> "MM"; DAY -> "dd"; MONTH_EN -> "MMM"
            WEEKDAY -> "EEEE"; WEEKDAY_EN -> "EEEE"; else -> error("Not a date field")
        }
        return DateTimeFormatter.ofPattern(pattern, if (this == WEEKDAY) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(date)
    }
}

internal object PhotoStickerDefaults {
    const val FILE = "photo-defaults.json"
    fun read(file: File, specs: List<VectorStickerGenerator>): Map<String, Map<String, PhotoStickerField>> {
        require(file.length() in 1..64L * 1024)
        val root = JSONObject(file.readText(Charsets.UTF_8)); require(root.getInt("schemaVersion") == 1)
        val definitions = root.getJSONObject("generators")
        require(definitions.length() <= specs.size)
        return definitions.keys().asSequence().associateWith { id ->
            val spec = requireNotNull(specs.find { it.id == id }) { "拍摄信息引用了不存在的贴纸" }
            val fields = definitions.getJSONObject(id); require(fields.length() <= 16)
            fields.keys().asSequence().associateWith { parameter ->
                require(spec.parameters.any { it.id == parameter && it.kind == StickerParameterKind.TEXT })
                PhotoStickerField.valueOf(fields.getString(parameter))
            }
        }
    }
    fun resolve(source: GeneratedSticker, fields: Map<String, PhotoStickerField>, facts: FrozenPhotoFacts): Map<String, String> =
        source.values + fields.mapValues { (id, field) ->
            field.text(facts).take(source.spec.parameters.first { it.id == id }.maxLength)
        }
}
