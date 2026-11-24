package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-port-xxhdpi")
class PolaroidActivityRotationUiTest {
    @get:Rule(order = 0) val guide = CompletedGuideRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realActivityRecreationKeepsTheFocusedPhotoPaintedAcrossBothOrientationsAndAllDockDetents() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val source = File(compose.activity.cacheDir, "album-rotation-fixture.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = model.stickers.importImage(Uri.fromFile(source)); source.delete()
        val album = model.cards.createAlbum("旋转验证")
        val card = model.cards.create(PolaroidCard(title = "", imageId = image.id), album.id)
        compose.runOnIdle { model.openSidePage(SidePage.CARDS) }
        compose.expandAlbumGroups()
        compose.onNodeWithTag("card-album/" + album.id).performClick()
        repeat(3) { pass ->
            listOf("zh-rCN-w852dp-h393dp-land-xxhdpi", "zh-rCN-w393dp-h852dp-port-xxhdpi").forEachIndexed { index, config ->
                RuntimeEnvironment.setQualifiers(config)
                compose.activityRule.scenario.recreate(); compose.waitForIdle()
                assertSame(model, ViewModelProvider(compose.activity)[AppViewModel::class.java])
                assertEquals(SidePage.CARDS, model.state.value.sidePage)
                compose.onNodeWithTag("card-album-photo/" + card.id).assertIsSelected()
                val output = File("build/previews/album-real-rotation-$pass-$index.png").apply { parentFile.mkdirs() }
                compose.waitUntil(8_000) {
                    compose.onNodeWithTag("polaroid-home").captureRoboImage(output.absolutePath)
                    val pixels = BitmapFactory.decodeFile(output.absolutePath)
                    try {
                        val home = compose.onNodeWithTag("polaroid-home").fetchSemanticsNode().boundsInRoot
                        val paper = compose.onNodeWithTag("polaroid/" + card.id).fetchSemanticsNode().layoutInfo.coordinates
                        val point = paper.localToRoot(Offset(paper.size.width / 2f, paper.size.height * .4f))
                        assertTrue("Photo stays inside the rotated viewport", home.contains(point))
                        val x = ((point.x - home.left) / home.width * pixels.width).toInt().coerceIn(0, pixels.width - 1)
                        val y = ((point.y - home.top) / home.height * pixels.height).toInt().coerceIn(0, pixels.height - 1)
                        val pixel = pixels.getPixel(x, y)
                        Color.red(pixel) > 220 && Color.green(pixel) < 10 && Color.blue(pixel) < 10
                    } finally { pixels.recycle() }
                }
            }
            compose.onNodeWithTag("toggle-card-albums").performClick()
            compose.onNodeWithTag("card-album-photos").assertDoesNotExist()
            compose.onNodeWithTag("toggle-card-albums").performClick()
            compose.onNodeWithTag("card-album-photos").assertIsDisplayed()
            compose.onNodeWithTag("card-albums").assertDoesNotExist()
            compose.expandAlbumGroups()
        }
    }
}
