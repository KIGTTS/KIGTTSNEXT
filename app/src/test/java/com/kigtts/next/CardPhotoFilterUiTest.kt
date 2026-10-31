package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
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
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CardPhotoFilterUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun filterAndStrengthUpdateTheVisibleDraftWithoutSavingOrChangingTheSource() {
        val app = ApplicationProvider.getApplicationContext<Application>(); val paths = PluginPaths(app)
        val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        val file = File(app.cacheDir, "live-filter-ui.png")
        val pixels = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(210, 60, 25)) }
        try { file.outputStream().use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { pixels.recycle() }
        val image = stickers.importResource(file); val bytes = stickers.imageFile(image.id)!!.readBytes()
        val saved = CardRepository(app, stickers).create(PolaroidCard(title = "", imageId = image.id, originalImageId = image.id))
        var draft by mutableStateOf(saved)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Column { PolaroidCanvas(draft, stickers::imageFile, Modifier.width(200.dp), frame = stickers.frameStyle(draft))
                CardPhotoFilterTools(draft, stickers, true, { draft = it }, { fail(it) }) }
        } }
        fun color(): Int {
            val output = File("build/previews/card-filter-live.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("card-photo/" + saved.id).captureRoboImage(output.absolutePath)
            val shot = BitmapFactory.decodeFile(output.absolutePath)
            return try { shot.getPixel(shot.width / 2, shot.height / 2) } finally { shot.recycle() }
        }
        compose.waitUntil(5000) { val value = color(); Color.red(value) > 200 && Color.blue(value) < 35 }
        compose.onNodeWithTag("camera-filter/BW").performClick()
        compose.waitUntil(5000) { val value = color(); kotlin.math.abs(Color.red(value) - Color.blue(value)) < 2 }
        assertEquals("BW", draft.photoStyle!!.filter)
        compose.onNodeWithContentDescription("滤镜强度").performSemanticsAction(SemanticsActions.SetProgress) { it(.25f) }
        compose.waitUntil(5000) { val value = color(); Color.red(value) - Color.blue(value) > 80 }
        assertEquals(.25f, draft.photoStyle!!.strength)
        compose.onNodeWithTag("camera-filter/ORIGINAL").performClick()
        compose.waitUntil(5000) { val value = color(); Color.red(value) > 200 && Color.blue(value) < 35 }
        assertNull(stickers.cards.value.first { it.id == saved.id }.photoStyle)
        assertArrayEquals(bytes, stickers.imageFile(image.id)!!.readBytes())
    }
}
