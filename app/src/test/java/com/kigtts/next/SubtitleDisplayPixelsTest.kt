package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.SubtitleDisplay
import com.kigtts.next.ui.home.SubtitleInput
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SubtitleDisplayPixelsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun frames(count: Int) { repeat(count) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
    private fun pixels(name: String): IntArray {
        val path = "build/previews/subtitle-display-$name.png"
        compose.onNodeWithTag("preview").captureRoboImage(path)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(path))
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height); bitmap.recycle()
        }
    }
    @Test fun incomingQuickTextNeverFlashesOpaqueBeforeFadingIn() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val baseState = model.state.value
        var incoming by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { MaterialTheme {
            Box(Modifier.size(320.dp, 300.dp).background(Color.White).testTag("preview")) {
                SubtitleDisplay(baseState.copy(subtitleRevision = if (incoming) 1 else 0), model,
                    if (incoming) "新字幕" else "旧字幕", TextStyle(fontSize = 60.sp, textAlign = TextAlign.Center,
                        color = if (incoming) Color.Blue else Color.Red), 320, 300)
            }
        } } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        compose.runOnIdle { incoming = true }
        val ink = (0 until 15).map { index ->
            frames(1)
            pixels("fade-$index").sumOf { pixel ->
                (android.graphics.Color.blue(pixel) - android.graphics.Color.red(pixel)).coerceAtLeast(0).toLong()
            }
        }
        assertTrue("Incoming text must become visible", ink.last() > 1000)
        assertTrue("The first rendered frame must not be fully opaque: $ink", ink.first() < ink.last() / 3)
        ink.zipWithNext().forEach { (before, after) -> assertTrue("No opaque-then-transparent flash: $ink", after >= before - 10) }
        compose.mainClock.autoAdvance = true
    }
    @Test fun reducedMotionStillFadesQuickTextWhileTheEditorIsOpen() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val baseState = model.state.value
        var incoming by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { MaterialTheme {
            Box(Modifier.size(320.dp, 300.dp).background(Color.White).testTag("preview")) {
                SubtitleInput(baseState.copy(editing = true, editorValue = TextFieldValue(if (incoming) "新字幕" else "旧字幕"),
                    subtitleRevision = if (incoming) 1 else 0, subtitleAppearance = SubtitleAppearance(reducedMotion = true)), model,
                    TextStyle(fontSize = 60.sp, textAlign = TextAlign.Center, color = if (incoming) Color.Blue else Color.Red),
                    320, 300, remember { FocusRequester() })
            }
        } } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        compose.runOnIdle { incoming = true }
        fun blueInk(name: String) = pixels(name).sumOf { pixel ->
            (android.graphics.Color.blue(pixel) - android.graphics.Color.green(pixel)).coerceAtLeast(0).toLong()
        }
        val ink = (0 until 20).map { index -> frames(1); blueInk("reduced-input-$index") }
        assertTrue("A quick text replacement retains intermediate fade frames in reduced mode: $ink",
            ink.last() > 1000 && ink.any { it > 0 && it < ink.last() * .8f })
        ink.zipWithNext().forEach { (before, after) -> assertTrue("Fade must remain continuous: $ink", after >= before - 10) }
        compose.mainClock.autoAdvance = true
    }
    @Test fun nonEditingReflowIsVisibleAndCanBeDisabledWithoutAResidualAnimation() = checkDisplayReflow(false)
    @Test fun placeholderUsesTheSameReflowAndHonorsTheMotionSetting() = checkDisplayReflow(true)
    private fun checkDisplayReflow(placeholder: Boolean) {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val baseState = model.state.value
        var width by mutableIntStateOf(300)
        var enabled by mutableStateOf(true)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { MaterialTheme {
            Box(Modifier.size(320.dp, 400.dp).background(Color.White).testTag("preview")) {
                Box(Modifier.width(width.dp).fillMaxHeight()) {
                    SubtitleDisplay(baseState.copy(subtitlePlaceholder = "甲乙丙丁戊己庚辛",
                        subtitleAppearance = SubtitleAppearance(displayReflow = enabled)), model,
                        if (placeholder) "" else "甲乙丙丁戊己庚辛",
                        TextStyle(fontSize = 40.sp, textAlign = TextAlign.Center, color = Color.Black), width, 400)
                }
            }
        } } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        compose.runOnIdle { width = 160 }
        frames(4); val middle = pixels("reflow-middle")
        frames(20); val end = pixels("reflow-end")
        assertTrue("Reflow must have an intermediate picture", middle.indices.count { middle[it] != end[it] } > 100)
        compose.runOnIdle { enabled = false; width = 300 }
        frames(4); val immediate = pixels("reflow-disabled")
        frames(20); val settled = pixels("reflow-disabled-settled")
        assertArrayEquals("Disabled reflow must settle immediately", immediate, settled)
        compose.mainClock.autoAdvance = true
    }
}
