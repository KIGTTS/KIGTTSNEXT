package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.audio.CaptionFontSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptionFontSettingsTest {
    @Test fun panelAndOverlaySizesReloadIndependently() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val preferences = CaptionFontSettings(app)
        assertEquals(20f, preferences.panel())
        assertEquals(22f, preferences.window())
        preferences.savePanel(32f)
        preferences.saveWindow(38f)
        val fresh = CaptionFontSettings(app)
        assertEquals(32f, fresh.panel())
        assertEquals(38f, fresh.window())
        val model = testAppModel(app)
        assertEquals(32f, model.state.value.captionTextSize)
        model.setCaptionTextSize(27f)
        assertEquals(27f, CaptionFontSettings(app).panel())
        assertEquals(38f, CaptionFontSettings(app).window())
    }
}
