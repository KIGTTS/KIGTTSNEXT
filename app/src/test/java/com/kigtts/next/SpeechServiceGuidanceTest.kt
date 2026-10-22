package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.audio.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpeechServiceGuidanceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @Before fun reset() { app.getSharedPreferences("speech-service-guidance", 0).edit().clear().commit() }
    @Test fun laterKeepsReadButtonAndRequestCanBeOpenedAgainWithoutInstalling() {
        var installs = 0
        val guidance = SpeechServiceGuidance(app, { SpeechServiceStatus.MISSING }, { installs++ }, {})
        assertTrue(guidance.visible.value); assertFalse(guidance.request())
        assertEquals(SpeechServiceStatus.MISSING, guidance.prompt.value)
        guidance.later(); assertNull(guidance.prompt.value); assertTrue(guidance.visible.value)
        assertFalse(guidance.request()); assertEquals(0, installs)
        guidance.proceed(); assertEquals(1, installs); assertNull(guidance.prompt.value)
    }
    @Test fun hidingPersistsUntilInstallationAndDisablingAlwaysHidesTheButton() {
        var status = SpeechServiceStatus.MISSING
        fun controller() = SpeechServiceGuidance(app, { status }, {}, {})
        val guidance = controller()
        guidance.request(); guidance.hide()
        assertFalse(guidance.visible.value); assertFalse(controller().visible.value)
        status = SpeechServiceStatus.ENABLED; guidance.refresh()
        assertTrue(guidance.visible.value); assertTrue(guidance.request()); assertNull(guidance.prompt.value)
        status = SpeechServiceStatus.UNAVAILABLE; guidance.refresh()
        assertFalse(guidance.visible.value); assertFalse(controller().visible.value)
        status = SpeechServiceStatus.ENABLED; guidance.refresh(); assertTrue(guidance.visible.value)
        status = SpeechServiceStatus.MISSING; guidance.refresh(); assertTrue(guidance.visible.value)
    }
    @Test fun unavailableServiceGoesToManagementWithoutDownloading() {
        var managed = 0
        val guidance = SpeechServiceGuidance(app, { SpeechServiceStatus.UNAVAILABLE }, { error("must not download") }, { managed++ })
        assertFalse(guidance.visible.value); assertFalse(guidance.request())
        guidance.proceed(); assertEquals(1, managed)
    }
}
