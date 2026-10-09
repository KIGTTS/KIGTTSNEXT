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
        compose.onNodeWithTag("play-motion-photo").performClick()
        compose.waitForIdle()
        assertTrue(expanded.value)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80);compose.waitForIdle()
        val middle=compose.onNodeWithTag("controls").getUnclippedBoundsInRoot()
        assertTrue(middle.right-middle.left>small.right-small.left)
        assertTrue(middle.right-middle.left<360.dp)
        compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
        val large=compose.onNodeWithTag("controls").getUnclippedBoundsInRoot()
        assertTrue(large.right-large.left>middle.right-middle.left);assertEquals(small.bottom-small.top,large.bottom-large.top)
        compose.onNodeWithContentDescription("返回照片").performClick()
        compose.waitForIdle()
        assertFalse(expanded.value)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
        assertEquals(small,compose.onNodeWithTag("controls").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("play-motion-photo").assertIsDisplayed()
    }

    @Test fun scrubPreviewsLocallyAndSeeksOnlyOnceWhenReleased() {
        val seeks=mutableListOf<Long>()
        val state=PlaybackState().apply {
            duration=10_000f
            player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,args ->
                if(method.name=="seekTo") { seeks+=args!!.last() as Long;null } else null
            } as Player
        }
        compose.setContent { MaterialTheme { Box(Modifier.width(360.dp)) { PlaybackControls(state,true,false,{}, {}) } } }
        val slider=compose.onNodeWithTag("media-wave-seek")
        slider.performTouchInput { down(centerLeft);moveTo(center) }
        compose.runOnIdle { assertTrue(state.scrubbing);assertTrue(state.position>0);assertTrue(seeks.isEmpty()) }
        slider.performTouchInput { moveTo(centerRight);up() }
        compose.runOnIdle { assertFalse(state.scrubbing);assertEquals(1,seeks.size);assertTrue(seeks.single()>8000) }
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
