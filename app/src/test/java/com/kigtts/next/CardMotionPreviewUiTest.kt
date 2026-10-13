@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.next.ui.media.LocalVideoPlayerFactory
import com.kigtts.plugin.api.*
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
class CardMotionPreviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun readOnlyPreviewOmitsEditAndMotionControlsStayBelowTheWholePaperWithExplicitPlaybackOnly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val card = PolaroidCard(title = "收到的实况", frameStyleId = "classic")
        val frame = PolaroidFrameStyle("classic", "经典")
        val overlay = Bitmap.createBitmap(576, 720, Bitmap.Config.ARGB_8888)
        val clip = CardMotionClip("image", File(app.cacheDir, "motion-preview-fixture.mp4"), 3000, 0, 1500, CardMotionRepository.identity())
        val preview = CardMotionPreview(clip, overlay, CardMotionPaperEffect(card, frame, overlay, clip.colorMatrix, true))
        val fixture = AudioPlayerFixture(arrayOf(ExoPlayer::class.java))
        compose.mainClock.autoAdvance = false
        compose.setContent { KigttsTheme(ThemeSettings()) { CompositionLocalProvider(LocalVideoPlayerFactory provides { fixture.player as ExoPlayer }) {
            CardPreviewDialog(card, { null }, frame, {}, {}, {}, {}, {}, {}, readOnly = true, motion = preview)
        } } }
        fun frames(count: Int = 4) { repeat(count) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
        frames(32)
        val paper = compose.onNodeWithTag("polaroid/" + card.id).fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("card-motion-controls").fetchSemanticsNode().boundsInRoot
        assertTrue(controls.top >= paper.bottom)
        compose.onNodeWithTag("edit-card-preview").assertDoesNotExist()
        compose.onNodeWithTag("share-card-preview").assertIsEnabled()
        assertEquals(0, fixture.prepares); assertFalse(fixture.playing)
        compose.onNodeWithTag("play-motion-photo").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
        frames(); compose.runOnIdle { fixture.firstFrame() }; frames()
        assertTrue(fixture.playing); assertEquals(0f, fixture.volume); assertEquals(1, fixture.videoEffects.size)
        compose.runOnIdle { fixture.player.pause(); preview.playback.seek(1200f) }; frames()
        assertEquals(1200L, fixture.position); assertFalse(fixture.playing)
        compose.runOnIdle { preview.playback.finishScrub(); fixture.end() }; frames(30)
        compose.onNodeWithTag("play-motion-photo").assertIsEnabled()
        assertFalse(preview.opened); assertFalse(fixture.playing); assertEquals(1, fixture.released)
    }
}
