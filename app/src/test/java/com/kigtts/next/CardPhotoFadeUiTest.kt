package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
class CardPhotoFadeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun firstLoadFadesAndLodReplacementNeverFlashesTheBackground() {
        fun blue(size: Int) = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }.asImageBitmap()
        var image by mutableStateOf<ImageBitmap?>(null)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CardPhotoBackground(PolaroidCard(id = "fade", title = "", imageId = "same"), image, false, {}, {},
                Modifier.size(200.dp).testTag("fade-scene"))
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        fun step(n: Int) { repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
        fun color(label: String): Int {
            val out = File("build/previews/photo-fade-$label.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("fade-scene").captureRoboImage(out.absolutePath)
            val bitmap = BitmapFactory.decodeFile(out.absolutePath)
            return try { bitmap.getPixel(bitmap.width / 4, bitmap.height / 4) } finally { bitmap.recycle() }
        }
        val empty = color("empty")
        compose.runOnIdle { image = blue(32) }; step(5)
        val halfway = color("first-half")
        assertTrue(Color.red(halfway) in 1 until Color.red(empty))
        assertTrue(Color.blue(halfway) > Color.blue(empty))
        step(20); assertEquals(Color.BLUE, color("loaded"))
        compose.runOnIdle { image = blue(256) }
        repeat(14) { step(1); assertEquals("LOD frame $it must not expose the gray backdrop", Color.BLUE, color("lod-$it")) }
        compose.runOnIdle { image = blue(512) }; step(3)
        compose.runOnIdle { image = blue(1024) }; step(3)
        assertEquals(Color.BLUE, color("interrupted"))
    }
}
