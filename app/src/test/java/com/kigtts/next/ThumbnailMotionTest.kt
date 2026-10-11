package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.media.MediaCardBackdrop
import com.kigtts.next.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class ThumbnailMotionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun step(ms:Long) { repeat(((ms+15)/16).toInt()) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() } }
    private fun sample(name:String):Int {
        val path="build/previews/thumbnail-$name.png"
        compose.onNodeWithTag("thumbnail-preview").captureRoboImage(path)
        val bitmap=BitmapFactory.decodeFile(path)
        return bitmap.getPixel(bitmap.width*4/5,bitmap.height/2).also { bitmap.recycle() }
    }
    @Test fun firstPreviewFadesAndRefreshKeepsOldPixelsUntilTheNewImageCrossfadesIn() {
        val model=testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val entry=model.mediaRepository.html("动画预览","<p>fixture</p>")
        compose.mainClock.autoAdvance=false
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(160.dp,88.dp).testTag("thumbnail-preview")) {
                MediaCardBackdrop(entry,model.mediaRepository,Modifier.fillMaxSize(),androidx.compose.ui.graphics.Color.Transparent)
            }
        } }
        fun submit(color:Int):Bitmap {
            val image=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            compose.runOnIdle { model.mediaRepository.thumbnails.rememberWeb(entry,image) };return image
        }
        fun awaitNew(previous:Bitmap?) {
            compose.waitUntil(10_000) {
                org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
                step(16)
                model.mediaRepository.thumbnails.latest(entry)?.let { it!==previous }==true
            }
            step(32)
        }
        try {
            val placeholder=sample("placeholder")
            submit(Color.BLUE);awaitNew(null)
            var entering=placeholder
            compose.waitUntil(10_000) {
                org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();step(16)
                entering=sample("first-enter-mid");entering!=placeholder
            }
            step(500)
            val blue=sample("first-ready")
            assertNotEquals(placeholder,entering);assertNotEquals(blue,entering);assertEquals(255,Color.blue(blue))
            val old=model.mediaRepository.thumbnails.latest(entry)
            submit(Color.RED);awaitNew(old)
            var mixed=blue
            compose.waitUntil(10_000) {
                org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();step(16)
                mixed=sample("update-mid");Color.red(mixed)>0
            }
            assertTrue(Color.red(mixed)>0);assertTrue(Color.blue(mixed)>0);assertEquals(255,Color.alpha(mixed))
            step(500);val red=sample("updated-ready");assertEquals(255,Color.red(red));assertEquals(0,Color.blue(red))
            compose.runOnIdle { model.mediaRepository.thumbnails.remove(entry.id) };step(500)
            assertEquals("Unavailable new preview must not blank the old prepared image",red,sample("refresh-unavailable"))
        } finally { compose.runOnIdle { model.mediaRepository.remove(entry.id) };compose.mainClock.autoAdvance=true }
    }
}
