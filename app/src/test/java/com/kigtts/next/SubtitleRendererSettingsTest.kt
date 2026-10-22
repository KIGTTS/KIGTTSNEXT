package com.kigtts.next

import com.kigtts.next.ui.media.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class SubtitleRendererSettingsTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun optionalSettingsAreBoundedAndInvalidDocumentsFallBackWithoutLosingText() {
        val root = temporary.newFolder()
        val file = root.resolve("renderer.json")
        assertEquals("{}", SubtitleRendererSettings.read(root))
        file.writeText("""{"color":"green","glow":"soft"}""")
        val settings = SubtitleRendererSettings.read(root)
        assertEquals("green", JSONObject(settings).getString("color"))
        val original = "引号\"与换行\n\u2028\u2029"
        val script = SubtitleRendererProtocol.update(original, "提示", Float.NaN, settings, MediaSafeArea(180, -1, 144, 24), 3f)
        assertFalse(script.contains('\u2028')); assertFalse(script.contains('\u2029'))
        val payload = JSONObject(script.substringAfter("r.update(").substringBeforeLast(");return true;"))
        assertEquals(original, payload.getString("text")); assertEquals(28.0, payload.getDouble("fontSizePercent"), 0.0)
        assertEquals("green", payload.getJSONObject("settings").getString("color"))
        assertEquals(60.0, payload.getJSONObject("safeArea").getDouble("top"), 0.0)
        assertEquals(48.0, payload.getJSONObject("safeArea").getDouble("bottom"), 0.0)
        assertEquals(0.0, payload.getJSONObject("safeArea").getDouble("right"), 0.0)
        assertEquals(8.0, payload.getJSONObject("safeArea").getDouble("left"), 0.0)
        file.writeText("[]"); assertEquals("{}", SubtitleRendererSettings.read(root))
        file.writeText(" ".repeat(4097)); assertEquals("{}", SubtitleRendererSettings.read(root))
    }
}
