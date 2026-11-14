package com.kigtts.next

import com.kigtts.next.cards.*
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraWatermarkFactsTest {
    private val xml = "<watermark version='1' id='sample' name='测试' layout='CLASSIC' "
    @Test fun legacyStylesStayOptOutAndOptionalMetadataFlagsRoundTrip() {
        val legacy = CameraWatermarkAsset.parse(xml + "/>").defaults
        assertTrue(watermarkRequestedFields(requireNotNull(CameraWatermarkStyle.decode(legacy.encode()))).isEmpty())
        val current = CameraWatermarkAsset.parse(xml + "location='true' weather='true'/>").defaults
        assertEquals(setOf(PhotoTextField.LOCATION, PhotoTextField.WEATHER), watermarkRequestedFields(current))
        assertEquals(current, CameraWatermarkStyle.decode(current.encode()))
        assertThrows(IllegalArgumentException::class.java) { CameraWatermarkAsset.parse(xml + "weather='yes'/>") }
    }
    @Test fun rendersRealFactsAndLabelsMissingFactsWithoutFakingWeather() {
        val style = CameraWatermarkAsset.parse(xml + "location='true' weather='true' exposure='true'/>").defaults
        val input = watermarkFacts(null, FrozenPhotoFacts(location = "上海\n徐汇", weather = "晴 22°C · 模型估计"))
        val triplet = watermarkLines(style, input.copy(date = "2026.11.14", time = "09:41", exposure = "ISO 100"))
        assertEquals(listOf("ISO 100", "2026.11.14  09:41", "上海 徐汇  ·  晴 22°C"), triplet.map { it.text })
        assertEquals("地点未知  ·  天气未知", watermarkLines(style, WatermarkFacts()).single().text)
    }
    @Test fun portableEditRetainsOnlyMetadataExplicitlyShownInWatermark() {
        val style = CameraWatermarkAsset.parse(xml + "location='true'/>").defaults
        val frame = PhotoFrameSpec(1200, 800, style.encode()).id
        val facts = FrozenPhotoFacts(1000, latitude = 25.1, longitude = 111.1, location = "上海", weather = "多云")
        val serialized = EmbeddedCameraEdit.encode(PolaroidCard(title = "", frameStyleId = frame), facts)
        val parsed = requireNotNull(EmbeddedCameraEdit.decode(serialized))
        assertEquals(facts.capturedAt, parsed.takenAt)
        assertEquals(facts.location, parsed.watermarkLocation)
        assertNull(parsed.watermarkWeather)
        assertFalse("111.1" in serialized)
    }
}
