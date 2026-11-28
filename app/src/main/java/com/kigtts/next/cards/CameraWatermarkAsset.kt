package com.kigtts.next.cards

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.security.MessageDigest

internal enum class WatermarkLayout { STANDARD, CHECK_IN, CLASSIC }
internal enum class WatermarkPosition(val label: String) { BOTTOM("底部"), TOP("顶部") }
internal enum class WatermarkPalette(val label: String, val color: Int) {
    LIGHT("浅色", 0xFFFAF8F4.toInt()), DARK("深色", 0xFF171E21.toInt()),
    WARM("暖色", 0xFFFFDFB6.toInt()), COOL("冷色", 0xFFD4ECEE.toInt()),
    GLASS("毛玻璃", 0xFF31383B.toInt()), WHITE("白色", -1), BLACK("黑色", 0xFF000000.toInt()),
    CUSTOM("自定义", -1)
}

internal enum class WatermarkGlassSource(val code: Int) {
    AUTO(0), TOP(1), BOTTOM(2), LEFT(3), RIGHT(4), FULL(5);
}

/** An immutable snapshot of the controls used for one photo, independent of installed assets. */
internal data class CameraWatermarkStyle(val layout: WatermarkLayout, val position: WatermarkPosition,
    val palette: WatermarkPalette, val scale: Int, val edge: Int, val band: Int, val showTime: Boolean,
    val showDate: Boolean, val showExposure: Boolean, val showLogo: Boolean, val center: Boolean = false,
    val showLocation: Boolean = false, val showWeather: Boolean = false,
    val glassSource: WatermarkGlassSource = WatermarkGlassSource.AUTO,
    val overlay: Boolean = false, val lockEdge: Boolean = false, val customColor: Int = -1) {
    val paperColor get() = if (palette == WatermarkPalette.CUSTOM) customColor else palette.color

    fun encode(): String = "wm1-" + listOf(layout.ordinal, position.ordinal, palette.ordinal, scale, edge, band,
        (if (showTime) 1 else 0) + (if (showDate) 2 else 0) + (if (showExposure) 4 else 0) +
            (if (showLogo) 8 else 0) + (if (center) 16 else 0) +
            (if (showLocation) 32 else 0) + (if (showWeather) 64 else 0) +
            (glassSource.code shl 7) + (if (overlay) 1024 else 0) + (if (lockEdge) 2048 else 0))
        .joinToString("-") { it.toString(36) } +
        (if (palette == WatermarkPalette.CUSTOM || customColor != -1) "-" + customColor.toUInt().toString(36) else "")

    companion object {
        fun decode(value: String): CameraWatermarkStyle? {
            if (!value.startsWith("wm1-")) return null
            val fields = value.substring(4).split('-')
            if (fields.size !in 7..8) return null
            val parts = fields.take(7).map { it.toIntOrNull(36) ?: return null }
            if (parts[3] !in 75..140 || parts[4] !in 0..12 ||
                parts[5] !in 8..24 || parts[6] !in 0..4095) return null
            val flags = parts[6]
            val glassSource = WatermarkGlassSource.entries.firstOrNull { it.code == ((flags ushr 7) and 7) } ?: return null
            val palette = WatermarkPalette.entries.getOrNull(parts[2]) ?: return null
            if (palette == WatermarkPalette.CUSTOM && fields.size != 8) return null
            val customColor = if (fields.size == 8) fields[7].toUIntOrNull(36)?.toInt() ?: return null else -1
            if (customColor ushr 24 != 255) return null
            return CameraWatermarkStyle(WatermarkLayout.entries.getOrNull(parts[0]) ?: return null,
                WatermarkPosition.entries.getOrNull(parts[1]) ?: return null, palette,
                parts[3], parts[4], parts[5], flags and 1 != 0, flags and 2 != 0, flags and 4 != 0, flags and 8 != 0,
                flags and 16 != 0, flags and 32 != 0, flags and 64 != 0, glassSource,
                overlay = flags and 1024 != 0, lockEdge = flags and 2048 != 0, customColor = customColor)
        }
    }
}

internal data class CameraWatermarkAsset(val id: String, val name: String, val defaults: CameraWatermarkStyle) {
    companion object {
        const val DIRECTORY = "camera-watermarks"
        const val MAX_XML = 4096
        private val idPattern = Regex("[a-z][a-z0-9-]{0,31}")
        fun parse(xml: String): CameraWatermarkAsset {
            require(xml.toByteArray(Charsets.UTF_8).size in 1..MAX_XML && !xml.contains("<!")) { "水印 XML 格式不正确" }
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(xml.reader())
            }
            require(parser.nextTag() == XmlPullParser.START_TAG && parser.name == "watermark" && parser.namespace.isNullOrEmpty())
            fun read(key: String) = parser.getAttributeValue(null, key)
            fun flag(key: String, default: Boolean) = read(key)?.let { it.toBooleanStrict() } ?: default
            fun number(key: String, default: Int, range: IntRange) =
                (read(key)?.toInt() ?: default).also { require(it in range) }
            val attributes = setOf("version", "id", "name", "layout", "position", "palette", "scale", "edge", "band",
                "time", "date", "exposure", "logo", "center", "location", "weather", "glassSource", "overlay", "lockEdge", "color")
            val names = (0 until parser.attributeCount).map { i ->
                require(parser.getAttributeNamespace(i).isNullOrEmpty())
                parser.getAttributeName(i)
            }
            require(names.all { it in attributes } && names.toSet().size == names.size)
            require(read("version") == "1")
            val id = requireNotNull(read("id")).also { require(idPattern.matches(it)) }
            val name = requireNotNull(read("name")).trim().also { require(it.length in 1..48 && it.none(Char::isISOControl)) }
            val style = CameraWatermarkStyle(WatermarkLayout.valueOf(requireNotNull(read("layout"))),
                WatermarkPosition.valueOf(read("position") ?: "BOTTOM"), WatermarkPalette.valueOf(read("palette") ?: "DARK"),
                number("scale", 100, 75..140), number("edge", 0, 0..12), number("band", 12, 8..24),
                flag("time", true), flag("date", true), flag("exposure", false), flag("logo", true), flag("center", false),
                flag("location", false), flag("weather", false),
                WatermarkGlassSource.valueOf(read("glassSource") ?: "AUTO"),
                overlay = flag("overlay", false), lockEdge = flag("lockEdge", false),
                customColor = read("color")?.let { color ->
                    require(color.matches(Regex("#[a-fA-F0-9]{6}")))
                    color.drop(1).toInt(16) or 0xFF000000.toInt()
                } ?: -1)
            require(CameraWatermarkStyle.decode(style.encode()) == style)
            require(parser.nextTag() == XmlPullParser.END_TAG && parser.next() == XmlPullParser.END_DOCUMENT)
            return CameraWatermarkAsset(id, name, style)
        }
    }
}

internal class CameraWatermarkLibrary(private val context: Context) {
    private val directory = File(context.filesDir, CameraWatermarkAsset.DIRECTORY)
    fun choices(): List<CameraWatermarkAsset> {
        val builtIn = listOf("standard", "check-in", "classic").map { name ->
            context.assets.open("camera-watermarks/$name.xml").bufferedReader(Charsets.UTF_8).use { CameraWatermarkAsset.parse(it.readText()) }
        }
        val installed = directory.listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }
            .sortedBy { it.name }.take(128).mapNotNull {
                runCatching { CameraWatermarkAsset.parse(read(it)).copy(id = it.nameWithoutExtension) }.getOrNull()
            }
        return builtIn + installed
    }

    fun install(packageId: String, file: File) {
        val xml = read(file)
        val asset = CameraWatermarkAsset.parse(xml)
        val key = MessageDigest.getInstance("SHA-256").digest("$packageId/${asset.id}".toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        check(directory.isDirectory || directory.mkdirs())
        val target = File(directory, "kpm-$key.xml")
        require(target.exists() || directory.listFiles().orEmpty().count { it.extension == "xml" } < 128) { "已达到水印数量上限" }
        val temp = File.createTempFile("watermark-", ".tmp", directory)
        try {
            temp.writeText(xml, Charsets.UTF_8)
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }

    companion object {
        fun read(file: File): String {
            require(file.isFile && file.length() in 1..CameraWatermarkAsset.MAX_XML.toLong())
            return file.readText(Charsets.UTF_8)
        }
    }
}
