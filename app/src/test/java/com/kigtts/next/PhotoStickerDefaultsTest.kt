package com.kigtts.next

import com.kigtts.next.cards.FrozenPhotoFacts
import com.kigtts.next.cards.generators.*
import com.kigtts.plugin.api.*
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStickerDefaultsTest {
    @Test @Config(sdk = [29, 35]) fun shippedInformationTemplatesRenderWithPhotoFactsAndUnknownDefaults() {
        val root = File("../distribution/resources/info-stickers")
        val specs = PluginJson.decodeFromString<StickerGenerators>(File(root,"generators.json").readText())
        val fields = PhotoStickerDefaults.read(File(root, PhotoStickerDefaults.FILE), specs.items)
        val facts = FrozenPhotoFacts(Instant.parse("2026-10-31T04:00:00Z").toEpochMilli(), "Asia/Shanghai", location="上海 · 漫展现场", weather="晴 22°C")
        assertEquals(14, specs.items.size)
        specs.items.forEach { spec ->
            val source = GeneratedSticker(spec, root, emptyMap())
            listOf(emptyMap(), PhotoStickerDefaults.resolve(source, fields.getValue(spec.id), facts)).forEach { values ->
                val bitmap = VectorStickerRenderer.render(spec, File(root, spec.template), spec.fonts.mapValues { File(root,it.value) }, values)
                try {
                    assertEquals(spec.width, bitmap.width)
                    var visible = false
                    for (y in 0 until bitmap.height step 7) for (x in 0 until bitmap.width step 7)
                        if (android.graphics.Color.alpha(bitmap.getPixel(x,y)) > 64) visible = true
                    assertTrue("Empty information sticker: ${spec.id}", visible)
                    assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0,0)))
                } finally { bitmap.recycle() }
            }
        }
    }
    @Test fun capturedDateUsesPhotoTimezoneAndUnknownNeverUsesToday() {
        val facts = FrozenPhotoFacts(Instant.parse("2026-10-30T23:20:00Z").toEpochMilli(), "Asia/Shanghai", location = "上海")
        assertEquals("2026.10.31", PhotoStickerField.DATE.text(facts))
        assertEquals("07:20", PhotoStickerField.TIME.text(facts))
        assertEquals("星期六", PhotoStickerField.WEEKDAY.text(facts))
        assertEquals("Saturday", PhotoStickerField.WEEKDAY_EN.text(facts))
        assertEquals("未知", PhotoStickerField.DATE.text(FrozenPhotoFacts()))
        assertEquals("未知", PhotoStickerField.WEATHER.text(facts))
    }
    @Test fun bindingsOnlyTargetDeclaredTextAndPreserveOtherParameters() {
        val spec = VectorStickerGenerator("clock", "时刻", "clock.svg", parameters = listOf(
            StickerParameter("time", "时间", StickerParameterKind.TEXT, "未知", maxLength = 20),
            StickerParameter("ink", "颜色", StickerParameterKind.COLOR, "#FFFFFF")))
        val file = File.createTempFile("photo-defaults", ".json")
        try {
            file.writeText("""{"schemaVersion":1,"generators":{"clock":{"time":"TIME"}}}""")
            val fields = PhotoStickerDefaults.read(file, listOf(spec)).getValue("clock")
            val source = GeneratedSticker(spec, file.parentFile!!, mapOf("ink" to "#112233"))
            assertEquals(mapOf("ink" to "#112233", "time" to "未知"), PhotoStickerDefaults.resolve(source, fields, FrozenPhotoFacts()))
            file.writeText("""{"schemaVersion":1,"generators":{"clock":{"ink":"LOCATION"}}}""")
            assertTrue(runCatching { PhotoStickerDefaults.read(file, listOf(spec)) }.isFailure)
            file.writeText("""{"schemaVersion":1,"generators":{"other":{"time":"TIME"}}}""")
            assertTrue(runCatching { PhotoStickerDefaults.read(file, listOf(spec)) }.isFailure)
        } finally { file.delete() }
    }
}
