package com.kigtts.next.ui.cards.camera

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraOptionsTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("camera-test", 0)
    @Before fun reset() { prefs.edit().clear().commit() }
    @Test fun cameraChoicesSurviveReopeningButTheSettingsDialogDoesNot() {
        val options = CameraOptions(prefs)
        options.setGrid(CameraGrid.SQUARE); options.setLevel(false); options.setFlash(CameraFlash.AUTO)
        options.setTimer(10); options.setProcessing(CameraProcessing.HDR); options.setFilterStrength(.4f); options.showSettings()
        val reopened = CameraOptions(prefs)
        assertEquals(CameraGrid.SQUARE, reopened.grid); assertFalse(reopened.level)
        assertEquals(CameraFlash.AUTO, reopened.flash); assertEquals(10, reopened.timerSeconds)
        assertEquals(CameraProcessing.HDR, reopened.processing)
        assertEquals(.4f, reopened.filterStrength, .001f)
        assertFalse(reopened.settingsVisible)
    }
    @Test fun unknownPreferencesRecoverAndUnsupportedCountdownsAreRejected() {
        prefs.edit().putString("grid", "future").putString("flash", "future").putString("processing", "future")
            .putFloat("filter-strength", Float.NaN).putInt("timer", -3).commit()
        val options = CameraOptions(prefs)
        assertEquals(CameraGrid.THIRDS, options.grid); assertEquals(CameraFlash.OFF, options.flash)
        assertEquals(CameraProcessing.STANDARD, options.processing)
        assertEquals(1f, options.filterStrength, .001f)
        options.setFilterStrength(Float.NaN); assertEquals(1f, CameraOptions(prefs).filterStrength, .001f)
        options.setFilterStrength(10f); assertEquals(1f, CameraOptions(prefs).filterStrength, .001f)
        options.setFilterStrength(-1f); assertEquals(0f, CameraOptions(prefs).filterStrength, .001f)
        assertEquals(0, options.timerSeconds)
        assertThrows(IllegalArgumentException::class.java) { options.setTimer(4) }
        assertEquals(0, CameraOptions(prefs).timerSeconds)
    }
}
