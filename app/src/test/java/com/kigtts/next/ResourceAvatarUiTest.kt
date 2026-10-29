package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.management.ResourceAvatar
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class ResourceAvatarUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val temp = TemporaryFolder()
    private fun center(id: String): Int {
        val path = "build/previews/resource-avatar-$id.png"
        compose.onNodeWithTag("managed-avatar/$id", useUnmergedTree = true).captureRoboImage(path)
        return BitmapFactory.decodeFile(path).let { image ->
            try { image.getPixel(image.width / 2, image.height / 2) } finally { image.recycle() }
        }
    }
    @Test fun cachedMonochromeVectorsFollowLiveThemeWhileColorArtworkStaysOriginal() {
        val svg = temp.newFile("glyph").apply { writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="8" fill="#cc8800"/></svg>""") }
        val png = temp.newFile("bitmap").apply {
            Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.BLUE)
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            }
        }
        var dark by mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme(onSecondaryContainer = Color(0xff44ddaa))
                else lightColorScheme(onSecondaryContainer = Color(0xffcc2244))) {
                Row {
                    ResourceAvatar(ManagementItem("themed","字形", image=PluginResource("glyph", monochrome=true)), { svg })
                    ResourceAvatar(ManagementItem("original","原色", image=PluginResource("glyph")), { svg })
                    ResourceAvatar(ManagementItem("bitmap","位图", image=PluginResource("bitmap", monochrome=true)), { png })
                }
            }
        }
        compose.waitUntil(10_000) { center("themed") == 0xffcc2244.toInt() }
        assertEquals(0xffcc8800.toInt(), center("original"))
        assertEquals(android.graphics.Color.BLUE, center("bitmap"))
        val modified = svg.lastModified()
        compose.runOnIdle { dark = true }
        compose.waitUntil(10_000) { center("themed") == 0xff44ddaa.toInt() }
        assertEquals(0xffcc8800.toInt(), center("original"))
        assertEquals(android.graphics.Color.BLUE, center("bitmap"))
        assertEquals("A theme change must not rewrite or reload the artwork", modified, svg.lastModified())
    }
}
