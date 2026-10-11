package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.KigttsApp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class InlineFontMotionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun step(ms:Long) { repeat(((ms+15)/16).toInt()) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() } }
    private fun pixels(label:String,name:String):IntArray {
        val path="build/previews/font-$name.png"
        compose.onNodeWithTag("font-swap-$label").captureRoboImage(path)
        val bitmap=BitmapFactory.decodeFile(path)
        return IntArray(bitmap.width*bitmap.height).also { bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height);bitmap.recycle() }
    }
    @Test fun bothInlineSlidersAnimateInAndOutWithStableToolbarHeightAndBackOnlyClosesTheSlider() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance","interaction","quick-phrases").forEach { app.getSharedPreferences(it,0).edit().clear().commit() }
        val model=testAppModel(app);compose.setContent { KigttsApp(model) }
        compose.runOnIdle { model.toggleListening() }
        listOf("字幕字号","聆听字号").forEach { label ->
            val area=compose.onNodeWithTag("font-swap-$label")
            val bounds=area.getUnclippedBoundsInRoot();val before=pixels(label,"$label-before")
            compose.mainClock.autoAdvance=false
            compose.onNodeWithContentDescription("调整$label").performClick();step(80)
            val entering=pixels(label,"$label-enter-mid");step(500)
            val after=pixels(label,"$label-open")
            assertFalse(before.contentEquals(entering));assertFalse(after.contentEquals(entering))
            assertEquals(bounds,area.getUnclippedBoundsInRoot())
            compose.onNodeWithContentDescription("完成${label}调整").performClick();step(64)
            val leaving=pixels(label,"$label-exit-mid");step(500)
            assertFalse(leaving.contentEquals(pixels(label,"$label-closed")))
            repeat(5) { compose.onNodeWithContentDescription("调整$label").performClick();step(16) }
            step(500);compose.onNodeWithTag("inline-$label").assertExists()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() };step(500)
            compose.onNodeWithTag("inline-$label").assertDoesNotExist();assertTrue(model.state.value.captionsVisible)
            assertEquals(bounds,area.getUnclippedBoundsInRoot());compose.mainClock.autoAdvance=true
        }
    }
}
