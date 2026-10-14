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
class PolaroidManagementRotationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun exercise(albums: Boolean) {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val source = File(compose.activity.cacheDir, "sort-rotation.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = model.stickers.importImage(Uri.fromFile(source))
        val album = model.cards.createAlbum("排序旋转")
        val card = model.cards.create(PolaroidCard(title = "", imageId = image.id), album.id)
        compose.runOnIdle { model.openSidePage(SidePage.CARDS) }
        compose.expandAlbumGroups()
        compose.onNodeWithTag("card-album/" + album.id).performClick()
        if (albums) compose.onNodeWithTag("card-album/" + album.id).performTouchInput { longClick() }
        else compose.onNodeWithTag("polaroid/" + card.id).performTouchInput { longClick() }
        compose.onNodeWithTag("card-management").assertIsDisplayed()
        repeat(2) { cycle ->
            listOf("zh-rCN-w852dp-h393dp-land-xxhdpi", "zh-rCN-w393dp-h852dp-port-xxhdpi").forEachIndexed { axis, config ->
                RuntimeEnvironment.setQualifiers(config)
                compose.activityRule.scenario.recreate(); compose.waitForIdle()
                compose.onNodeWithTag(if (albums) "manage-album/${album.id}" else "manage-photo/${card.id}").assertIsSelected()
                val header = compose.onNodeWithTag(if (axis == 0) "small-page-bar" else "large-page-bar").getUnclippedBoundsInRoot()
                val count = compose.onNodeWithTag("card-management-count").getUnclippedBoundsInRoot()
                assertTrue("Sorting content must start below the app bar", count.top >= header.bottom)
                val dock = compose.onNodeWithTag("card-album-dock").fetchSemanticsNode().boundsInRoot
                val photo = compose.onNodeWithTag("card-album-photo/${card.id}").fetchSemanticsNode().layoutInfo.coordinates
                val a = photo.localToRoot(Offset.Zero); val b = photo.localToRoot(Offset(photo.size.width.toFloat(), photo.size.height.toFloat()))
                assertTrue("The retained panel must not inherit a miniature layer transform", b.x - a.x > 120f && b.y - a.y > 120f)
                val point = photo.localToRoot(Offset(photo.size.width * .5f, photo.size.height * .4f))
                assertTrue("Photo remains inside its panel after recreation", dock.contains(point))
                val output = File("build/previews/management-rotation-$albums-$cycle-$axis.png").apply { parentFile?.mkdirs() }
                compose.waitUntil(8000) {
                    compose.onNodeWithTag("card-album-dock").captureRoboImage(output.absolutePath)
                    val pixels = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
                    try {
                        val pixel = pixels.getPixel(((point.x - dock.left) / dock.width * pixels.width).toInt(),
                            ((point.y - dock.top) / dock.height * pixels.height).toInt())
                        Color.red(pixel) > 220 && Color.green(pixel) < 10 && Color.blue(pixel) < 10
                    } finally { pixels.recycle() }
                }
            }
        }
        compose.onNodeWithTag("cards-manage-done").performClick()
        compose.onNodeWithTag("card-management").assertDoesNotExist()
        compose.onNodeWithTag("toggle-card-albums").performClick()
        compose.onNodeWithTag("card-album-photos").assertDoesNotExist()
        compose.expandAlbumGroups()
        compose.onNodeWithTag("card-album-photo/${card.id}").assertIsDisplayed()
    }
    @Test fun albumSortingAvoidsTheHeaderAndKeepsTheDockAcrossActivityRecreation() = exercise(true)
    @Test fun photoSortingAvoidsTheHeaderAndKeepsTheDockAcrossActivityRecreation() = exercise(false)
}
