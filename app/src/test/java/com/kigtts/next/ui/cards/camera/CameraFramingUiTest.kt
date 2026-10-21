package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.CardFrameRequest
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.PolaroidFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import com.github.takahirom.roborazzi.captureRoboImage

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h1000dp-xhdpi")
class CameraFramingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val files = TemporaryFolder()
    private fun check(landscape: Boolean) {
        val source = files.newFile("original.png")
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        try { source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        val original = source.readBytes(); var confirmed: PolaroidFormat? = null
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(if (landscape) 820.dp else 393.dp, if (landscape) 393.dp else 820.dp)) {
                CameraFramingScreen(CardFrameRequest("test", source, source), false, {}, { format, _, _, _ -> confirmed = format })
            }
        } }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("capture-frame-confirm").assertIsEnabled() }.isSuccess }
        compose.onNodeWithText("使用照片").assertDoesNotExist()
        compose.onNodeWithContentDescription("使用照片").assertIsDisplayed()
        compose.onNodeWithText("双指调整位置与缩放").assertIsDisplayed()
        for (format in captureFrameFormats) {
            if (landscape) compose.onNodeWithTag("capture-format/" + format.name).performScrollTo()
            val paper = compose.onNodeWithTag("capture-format-paper/" + format.name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(format.geometry.aspectRatio, (paper.width / paper.height).toDouble(), .02)
            compose.onNodeWithTag("capture-format/" + format.name).performClick().assertIsSelected()
        }
        compose.onRoot().captureRoboImage("build/previews/capture-paper-tabs-${if (landscape) "landscape" else "portrait"}.png")
        compose.onNodeWithTag("capture-frame-confirm").performTouchInput { click() }
        assertEquals(PolaroidFormat.WIDE, confirmed); assertArrayEquals(original, source.readBytes())
    }
    @Test fun portraitHasPaperTabsAndOnlyAnIconToConfirm() = check(false)
    @Test fun landscapeHasTheSamePaperTabsOnTheRight() = check(true)
}
