package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.audio.SpeechServiceStatus
import com.kigtts.next.state.AppState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomaticReadingTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @Before fun reset() {
        listOf("playback", "speech-service-guidance").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
    }
    @Test fun freshInstallationIsOffAndExistingExplicitChoicesSurviveRestart() {
        assertFalse(AppState().autoPlayback)
        assertFalse(testAppModel(app).state.value.autoPlayback)
        app.getSharedPreferences("playback", 0).edit().putBoolean("automatic", true).commit()
        val restored = testAppModel(app)
        assertTrue(restored.state.value.autoPlayback)
        restored.setAutoPlayback(false)
        assertFalse(testAppModel(app).state.value.autoPlayback)
    }
    @Test fun enablingWithoutServiceUsesReadGuidanceWithoutSavingAnUnusableOnState() {
        val model = testAppModel(app)
        val services = model.registry.plugins
        model.registry.replace(services.filter { it.tts == null })
        model.synthesisGuidance.refresh()
        model.toggleAutoPlayback()
        assertEquals(SpeechServiceStatus.MISSING, model.synthesisGuidance.prompt.value)
        assertFalse(model.state.value.autoPlayback)
        assertFalse(app.getSharedPreferences("playback", 0).contains("automatic"))
        model.synthesisGuidance.later()
        model.text("测试朗读")
        model.speak()
        assertEquals(SpeechServiceStatus.MISSING, model.synthesisGuidance.prompt.value)
        model.synthesisGuidance.hide()
        assertFalse(model.synthesisGuidance.visible.value)
        model.registry.replace(services)
        model.synthesisGuidance.refresh()
        model.toggleAutoPlayback()
        assertNull(model.synthesisGuidance.prompt.value)
        assertTrue(model.state.value.autoPlayback)
        assertTrue(testAppModel(app).state.value.autoPlayback)
    }
}
