package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.navigation.HomeExitHandler
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class HomeExitUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clearPreference() { app.getSharedPreferences("interaction", 0).edit().remove("double-back-exit").commit() }
    private fun back() { compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    @Test fun firstBackAsksThenKeepsTheChosenHabitAndOnlySecondBackLeaves() {
        val preference = HomeExitPreference(app)
        var leaves = 0; val hints = mutableListOf<String>(); var now = 10L
        compose.setContent { MaterialTheme { HomeExitHandler(preference, true, { hints += it }, { leaves++ }, { now }) } }
        back()
        compose.onNodeWithText("选择返回习惯").assertIsDisplayed()
        assertNull(preference.doubleBack.value); assertEquals(0, leaves)
        compose.onNodeWithText("确定").performClick(); compose.waitForIdle()
        assertEquals(true, HomeExitPreference(app).doubleBack.value)
        back(); assertEquals(listOf("再按一次返回键退出"), hints); assertEquals(0, leaves)
        now += 500L; back(); assertEquals(1, leaves)
    }
    @Test fun choosingSingleBackIsRememberedAndDismissalDoesNotChooseForTheUser() {
        val preference = HomeExitPreference(app); var leaves = 0
        compose.setContent { MaterialTheme { HomeExitHandler(preference, true, {}, { leaves++ }) } }
        back(); compose.onNodeWithText("稍后再说").performClick(); compose.waitForIdle()
        assertNull(preference.doubleBack.value); assertEquals(0, leaves)
        back(); compose.onNodeWithText("按一次返回键退出").performClick()
        compose.onNodeWithText("确定").performClick(); compose.waitForIdle()
        assertEquals(false, HomeExitPreference(app).doubleBack.value)
        assertEquals(0, leaves); back(); assertEquals(1, leaves)
    }
    @Test fun settingsSwitchPersistsAndChildPageBackDoesNotShowExitDialog() {
        val model = AppViewModel(app)
        model.navigate(Destination.SettingsDetail(SettingsCategory.FEEDBACK))
        compose.setContent { KigttsApp(model) }
        compose.onNodeWithContentDescription("二次返回退出").performScrollTo().assertIsOn().performClick()
        compose.runOnIdle { assertEquals(false, model.homeExit.doubleBack.value) }
        compose.onNodeWithContentDescription("二次返回退出").performClick()
        compose.runOnIdle { assertEquals(true, HomeExitPreference(app).doubleBack.value) }
        back(); compose.onNodeWithText("选择返回习惯").assertDoesNotExist()
        compose.runOnIdle { assertNotEquals(Destination.SettingsDetail(SettingsCategory.FEEDBACK), model.state.value.destination) }
    }
}
