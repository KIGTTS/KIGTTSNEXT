package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import android.graphics.BitmapFactory
import androidx.media3.common.Player
import com.kigtts.next.ui.media.PlaybackControls
import com.kigtts.next.ui.media.PlaybackState
import com.kigtts.next.ui.media.MediaWaveTrack
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="w393dp-h852dp-xxhdpi")
class PlaybackControlsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun liveButtonExpandsAndContractsWithinTheSamePill() {
        val expanded=mutableStateOf(false);val state=PlaybackState()
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme {
            Box(Modifier.width(360.dp)) { PlaybackControls(state,expanded.value,true,{ expanded.value=true },{ expanded.value=false },Modifier.testTag("controls")) }
        } }
        compose.waitForIdle()
        val small=compose.onNodeWithTag("controls").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("controls").captureRoboImage("build/previews/live-controls-compact.png")
        compose.onNodeWithTag("play-motion-photo").performClick()
        compose.waitForIdle()
        assertTrue(expanded.value)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80);compose.waitForIdle()
        val middle=compose.onNodeWithTag("controls").getUnclippedBoundsInRoot()
        assertTrue(middle.right-middle.left>small.right-small.left)
        assertTrue(middle.right-middle.left<360.dp)
        compose.mainClock.advanceTimeBy(160);compose.waitForIdle()
        val rebound=compose.onNodeWithTag("controls").getUnclippedBoundsInRoot()
        compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
        val large=compose.onNodeWithTag("controls").getUnclippedBoundsInRoot()
        assertTrue(large.right-large.left>middle.right-middle.left)
        assertTrue(rebound.right-rebound.left>large.right-large.left)
        assertTrue(rebound.right-rebound.left<=360.dp)
        assertEquals(40.dp,small.bottom-small.top);assertEquals(48.dp,large.bottom-large.top)
        compose.onNodeWithTag("controls").captureRoboImage("build/previews/live-controls-expanded.png")
        compose.onNodeWithContentDescription("返回照片").performClick()
        compose.waitForIdle()
        assertFalse(expanded.value)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80);compose.waitForIdle()
        val closing=compose.onNodeWithTag("controls").getUnclippedBoundsInRoot()
        assertTrue(closing.right-closing.left>small.right-small.left)
        assertTrue(closing.right-closing.left<large.right-large.left)
        compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
        assertEquals(small,compose.onNodeWithTag("controls").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("play-motion-photo").assertIsDisplayed()
    }

    @Test fun pressingCompactPlayMorphsItsShapeAndCancellingRestoresItWithoutPlayback() {
        var starts=0;val state=PlaybackState()
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme {
            Box(Modifier.width(360.dp)) { PlaybackControls(state,false,true,{ starts++ },{},Modifier.testTag("controls")) }
        } }
        compose.waitForIdle()
        val button=compose.onNodeWithTag("play-motion-photo")
        val rest=button.fetchSemanticsNode().boundsInRoot
        button.performTouchInput { down(center) }
        repeat(10) { compose.mainClock.advanceTimeBy(32);compose.waitForIdle() }
        val held=button.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("controls").captureRoboImage("build/previews/live-controls-pressed.png")
        assertTrue(held.right-held.left<rest.right-rest.left)
        assertTrue(held.bottom-held.top<rest.bottom-rest.top)
        button.performTouchInput { cancel() }
        compose.waitForIdle();compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
        assertEquals(rest,button.fetchSemanticsNode().boundsInRoot);assertEquals(0,starts)
        button.performClick();compose.runOnIdle { assertEquals(1,starts) }
    }

    @Test fun scrubPausesAndSeeksDuringEveryMoveThenRestoresPreviousPlayback() {
        val fixture=AudioPlayerFixture()
        val state=PlaybackState().apply {
            duration=10_000f
            player=fixture.player
        }
        fixture.player.play()
        compose.setContent { MaterialTheme { Box(Modifier.width(360.dp)) { PlaybackControls(state,true,false,{}, {}) } } }
        val slider=compose.onNodeWithTag("media-wave-seek")
        slider.performTouchInput { down(centerLeft);moveTo(center) }
        compose.runOnIdle { assertTrue(state.scrubbing);assertTrue(state.position>0);assertFalse(fixture.playing);assertTrue(fixture.seeks.isNotEmpty()) }
        val initial=fixture.seeks.size
        slider.performTouchInput { moveTo(centerRight);up() }
        compose.runOnIdle { assertFalse(state.scrubbing);assertTrue(fixture.seeks.size>initial);assertTrue(fixture.seeks.last()>8000);assertTrue(fixture.playing) }
        compose.runOnIdle { fixture.player.pause() }
        slider.performTouchInput { down(centerRight);moveTo(center);up() }
        compose.runOnIdle { assertFalse(state.scrubbing);assertFalse(fixture.playing) }
    }

    @Test fun idlePlaybackNarrowsAndPausingOrScrubbingRestoresControls() {
        val fixture=AudioPlayerFixture();val state=PlaybackState().apply { player=fixture.player;duration=3000f;playing=true }
        fixture.player.play()
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { Box(Modifier.width(360.dp)) { PlaybackControls(state,true,false,{}, {}) } } }
        compose.waitForIdle()
        fun buttonWidth()=compose.onNodeWithContentDescription("切换声音").getUnclippedBoundsInRoot().let { it.right-it.left }
        val full=buttonWidth()
        repeat(250) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
        val small=buttonWidth()
        assertTrue("Idle button slot must collapse",small<full/2)
        compose.onNodeWithContentDescription("切换声音").assertIsNotEnabled()
        val slider=compose.onNodeWithTag("media-wave-seek")
        slider.performTouchInput { down(centerRight);moveTo(androidx.compose.ui.geometry.Offset(width*.85f,center.y)) }
        repeat(20) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
        assertFalse(fixture.playing);assertTrue(state.scrubbing)
        assertTrue(buttonWidth()>small)
        slider.performTouchInput { up() }
        compose.runOnIdle { assertTrue(fixture.playing);state.playing=false;fixture.player.pause() }
        repeat(30) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
        assertEquals(full,buttonWidth())
    }

    @Test fun pauseMorphsWaveIntoStraightTrackAndResumeRestoresIt() {
        val playing=mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { MediaWaveTrack({ .6f },playing.value,Modifier.width(240.dp).testTag("wave")) } }
        fun paintedHeight(name:String):Int {
            val file="build/previews/wave-$name.png"
            compose.onNodeWithTag("wave").captureRoboImage(file)
            val bitmap=BitmapFactory.decodeFile(file)
            val rows=(0 until bitmap.height).filter { y ->
                (bitmap.width/10 until bitmap.width/2).any { x -> android.graphics.Color.green(bitmap.getPixel(x,y))<160 }
            }
            bitmap.recycle();return rows.last()-rows.first()+1
        }
        compose.waitForIdle();compose.mainClock.advanceTimeBy(100)
        val moving=paintedHeight("playing")
        compose.runOnIdle { playing.value=false };compose.waitForIdle();compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        val slowing=paintedHeight("slowing")
        compose.mainClock.advanceTimeBy(500)
        val paused=paintedHeight("paused")
        assertTrue(moving>paused);assertTrue(slowing>paused)
        compose.runOnIdle { playing.value=true };compose.waitForIdle();compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(500)
        assertTrue(paintedHeight("resumed")>paused)
    }
}
