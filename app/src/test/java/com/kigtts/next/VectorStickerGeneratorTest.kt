package com.kigtts.next

import android.graphics.Color
import com.kigtts.next.cards.generators.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class VectorStickerGeneratorTest {
    private fun file(text: String) = File.createTempFile("vector-template", ".svg").apply { writeText(text) }
    private val spec = VectorStickerGenerator("art", "艺术字", "template.svg", 256, 128, listOf(
        StickerParameter("title", "文字", StickerParameterKind.TEXT, "你好"),
        StickerParameter("color", "颜色", StickerParameterKind.COLOR, "#FF0000"),
        StickerParameter("size", "大小", StickerParameterKind.NUMBER, "32", 12.0, 64.0)))
    @Test fun typedParametersRenderAndTextCannotInjectMarkup() {
        val source = file("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 128"><rect width="256" height="128" fill="{{color}}"/><text x="128" y="80" font-size="{{size}}" text-anchor="middle" fill="#FFFFFF">{{title}}</text></svg>""")
        val one = VectorStickerRenderer.render(spec, source, emptyMap(), emptyMap())
        val two = VectorStickerRenderer.render(spec, source, emptyMap(), mapOf("color" to "#0000FF", "title" to "<path onclick='bad'/>"))
        try { assertEquals(Color.RED, one.getPixel(0,0)); assertEquals(Color.BLUE, two.getPixel(0,0)); assertEquals(256, two.width) }
        finally { one.recycle(); two.recycle(); source.delete() }
    }
    @Test fun rejectsExternalEntitiesUrlsUnknownParametersAndOutOfRangeValues() {
        listOf("<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///private'>]><svg/>",
            """<svg xmlns="http://www.w3.org/2000/svg"><image href="https://example.test/track"/></svg>""",
            """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""").forEach { xml ->
            val source = file(xml)
            try { assertTrue(runCatching { VectorStickerRenderer.render(spec, source, emptyMap(), emptyMap()) }.isFailure) }
            finally { source.delete() }
        }
        assertTrue(runCatching { VectorStickerRenderer.values(spec, mapOf("size" to "NaN")) }.isFailure)
        assertTrue(runCatching { VectorStickerRenderer.values(spec, mapOf("size" to "100")) }.isFailure)
        assertTrue(runCatching { VectorStickerRenderer.values(spec, mapOf("unknown" to "x")) }.isFailure)
    }
    @Test fun literalBracesDollarBackslashAndMultilingualTextStayInsideOneSvgTextNode() {
        val source = file("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 128"><text x="128" y="80" font-size="{{size}}" text-anchor="middle" fill="{{color}}">{{title}}</text></svg>""")
        val image = VectorStickerRenderer.render(spec, source, emptyMap(), mapOf("title" to "你好 {{title}} ${'$'}1 \\ &amp;"))
        try { assertEquals(256, image.width); assertEquals(128, image.height) }
        finally { image.recycle(); source.delete() }
    }
}
