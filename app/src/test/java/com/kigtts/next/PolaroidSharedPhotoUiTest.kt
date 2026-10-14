package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class PolaroidSharedPhotoUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun stackGridAndSortingMoveTheSameOpaquePhotoContinuouslyAndCanReverse() {
        val file = File(compose.activity.cacheDir, "shared-blue.png")
        val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val card = PolaroidCard(id = "shared-photo", title = "", imageId = "blue")
        var mode by mutableStateOf(PolaroidBrowseMode.STACK)
        var managing by mutableStateOf(false)
        var ready by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            val cached = listOf(256, 512, 1024, 1200).map { rememberCardImage(file, it) }
            SideEffect { ready = cached.all { it != null } }
            Box(Modifier.fillMaxSize().testTag("shared-photo-scene")) {
                CardManagementTransition(managing, browser = {
                    PolaroidBrowser(listOf(card), mode, card.id, { file }, emptyList(), !managing,
                        { mode = it }, {}, {}, {}, Modifier.fillMaxSize())
                }, manager = {
                    Row(Modifier.fillMaxSize().padding(top = 80.dp), horizontalArrangement = Arrangement.Center) {
                        PolaroidCanvas(card, { file }, Modifier.width(100.dp))
                    }
                })
            }
        } }
        compose.waitUntil(8000) { ready }; compose.waitForIdle(); compose.mainClock.autoAdvance = false
        fun frame() { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        fun photo(name: String): Rect {
            val output = File("build/previews/shared-photo-$name.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("shared-photo-scene").captureRoboImage(output.absolutePath)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
            try {
                var left = bitmap.width; var top = bitmap.height; var right = 0; var bottom = 0; var pixels = 0
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.blue(color) > 235 && Color.red(color) < 15 && Color.green(color) < 15) {
                        left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y); pixels++
                    }
                }
                assertTrue("The actual photo stays opaque and visible throughout $name", pixels > 500)
                assertTrue("There must be one contiguous photo, not two fading copies ($name)", pixels > (right - left) * (bottom - top) * .85f)
                return Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            } finally { bitmap.recycle() }
        }
        val original = photo("stack")
        compose.runOnIdle { mode = PolaroidBrowseMode.GRID }; repeat(3) { frame() }
        val early = photo("grid-early"); repeat(7) { frame() }; val middle = photo("grid-middle")
        assertTrue(early.width > middle.width + 4f); assertTrue(early.width < original.width + 4f)
        compose.runOnIdle { mode = PolaroidBrowseMode.STACK }; repeat(4) { frame() }
        photo("grid-reverse"); repeat(30) { frame() }; val returned = photo("stack-returned")
        assertEquals(original.width, returned.width, 3f)
        compose.runOnIdle { managing = true }; repeat(3) { frame() }; val sortingStart = photo("sort-early")
        repeat(7) { frame() }; val sortingMiddle = photo("sort-middle")
        assertTrue(sortingStart.width > sortingMiddle.width + 4f)
        repeat(30) { frame() }; val sorted = photo("sort-settled")
        assertTrue(sorted.width < sortingMiddle.width)
        compose.runOnIdle { managing = false }; repeat(7) { frame() }; photo("sort-return")
        repeat(30) { frame() }; assertEquals(original.width, photo("final").width, 3f)
    }
}
