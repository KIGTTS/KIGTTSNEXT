package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.*
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.settings.AppearanceSettingsScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SubtitleMotionPreferencesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @Before fun reset() { app.getSharedPreferences("subtitle-appearance", 0).edit().clear().commit() }

    @Test fun settingsPersistBothChoicesAndReducedMotionTemporarilyDisablesReflow() {
        val store = SubtitleAppearanceStore(app)
        var value by mutableStateOf(store.load())
        assertFalse(value.reducedMotion); assertTrue(value.displayReflow)
        compose.setContent { MaterialTheme {
            AppearanceSettingsScreen(ThemeSettings(), {}, subtitle = value, onSubtitleChange = { value = it; store.save(it) })
        } }
        val reflow = compose.onNodeWithContentDescription(app.uiCopy("appearance.reflow", locale = "zh-CN"))
        val reduced = compose.onNodeWithContentDescription(app.uiCopy("appearance.less_motion", locale = "zh-CN"))
        reflow.performScrollTo().assertIsOn().performClick().assertIsOff()
        reduced.performScrollTo().performClick().assertIsOn()
        reflow.assertIsNotEnabled().assertIsOff()
        assertEquals(value, SubtitleAppearanceStore(app).load())
        reduced.performClick().assertIsOff()
        reflow.assertIsEnabled().assertIsOff()
        reflow.performScrollTo().performClick().assertIsOn()
        assertFalse(store.load().reducedMotion); assertTrue(store.load().displayReflow)
    }
}
