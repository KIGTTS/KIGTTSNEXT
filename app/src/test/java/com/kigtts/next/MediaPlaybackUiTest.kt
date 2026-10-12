@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.next.ui.media.*
import com.kigtts.next.ui.navigation.LocalPageActive
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="w393dp-h852dp-xxhdpi")
class MediaPlaybackUiTest {
    @Test fun pausedAndEndedVideoResizeDoesNotRequestAnyFramePlaybackOrSeek() {
        val fixture=AudioPlayerFixture(arrayOf(ExoPlayer::class.java));val playback=PlaybackState()
        val height=mutableStateOf(220.dp);val clip=file()
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalVideoPlayerFactory provides { fixture.player as ExoPlayer }) {
            MediaVideo(clip,"视频",false,{},Modifier.size(300.dp,height.value),playback=playback,autoPlay=false)
        } } }
        change { fixture.firstFrame();fixture.player.pause();height.value=400.dp }
        compose.mainClock.advanceTimeBy(120);compose.waitForIdle()
        compose.runOnIdle { assertFalse(fixture.playing);assertTrue(fixture.seeks.isEmpty());assertEquals(0,fixture.redraws);fixture.end();fixture.playWhenReady=true;height.value=160.dp }
        compose.mainClock.advanceTimeBy(120);compose.waitForIdle()
        compose.runOnIdle { assertEquals(androidx.media3.common.Player.STATE_ENDED,fixture.state);assertTrue(fixture.seeks.isEmpty());assertFalse(fixture.playing) }
    }
    @Test fun rapidResizingPreservesNativeSourceAspectAndPausedFrameAcrossAllThreePlaybackStates() {
        val fixture=AudioPlayerFixture(arrayOf(ExoPlayer::class.java));val playback=PlaybackState();val clip=file()
        val height=mutableStateOf(400.dp);val scale=mutableStateOf(MediaScale.FIT)
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalVideoPlayerFactory provides { fixture.player as ExoPlayer }) {
            MediaVideo(clip,"视频",false,{},Modifier.size(300.dp,height.value),playback=playback,autoPlay=true,scale=scale.value)
        } } }
        // The effects renderer does not emit Player.VideoSize. Only processed output configures the raster.
        change { fixture.processedSize(640,480);fixture.firstFrame() }
        repeat(3) { phase ->
            change { if(phase==1) { fixture.player.pause();fixture.position=1250 } else if(phase==2)fixture.end() }
            val position=fixture.position
            repeat(16) { index ->
                change { height.value=listOf(400.dp,110.dp,520.dp,170.dp)[index%4];scale.value=if(index%3==0)MediaScale.FILL else MediaScale.FIT }
                val frame=compose.onNodeWithTag("media-video-foreground",useUnmergedTree=true).getUnclippedBoundsInRoot()
                assertEquals(4f/3f,(frame.right-frame.left).value/(frame.bottom-frame.top).value,.015f)
                compose.runOnIdle { assertEquals(position,fixture.position);assertEquals(phase==0,fixture.playing);assertEquals(0,fixture.redraws);assertTrue(fixture.seeks.isEmpty()) }
                compose.runOnIdle {
                    // Native Surface dimensions, not just transformed Compose bounds, must stay
                    // constant: resizing a Surface can render queued frames on a paused player.
                    assertEquals(640,fixture.videoView!!.width);assertEquals(480,fixture.videoView!!.height)
                    fun outputs(view:android.view.View):List<android.view.TextureView> = when(view) {
                        is android.view.TextureView->listOf(view)
                        is android.view.ViewGroup->(0 until view.childCount).flatMap { outputs(view.getChildAt(it)) }
                        else->emptyList()
                    }
                    val surfaces=outputs(compose.activity.window.decorView)
                    assertEquals(2,surfaces.size)
                    surfaces.forEach { assertEquals(640,it.width);assertEquals(480,it.height) }
                }
            }
        }
    }
    @Test fun highResolutionLivePhotoUsesProcessedAspectAndOccupiesTheFullFitHeightWithoutPlayerSizeEvents() {
        val fixture=AudioPlayerFixture(arrayOf(ExoPlayer::class.java));val live=LivePhotoState();val clip=file()
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalVideoPlayerFactory provides { fixture.player as ExoPlayer }) {
            Box(Modifier.size(360.dp,180.dp)) { LivePhotoPlayback(clip,"实况",MediaScale.FIT,true,live,showControls=false) }
        } } }
        change { } // Autoplay mounts its player on the next Compose frame.
        change { fixture.processedSize(1440,1080);fixture.firstFrame() }
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        fun displayedSize(tag:String):androidx.compose.ui.geometry.Offset {
            val coordinates=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().layoutInfo.coordinates
            return coordinates.localToRoot(androidx.compose.ui.geometry.Offset(coordinates.size.width.toFloat(),coordinates.size.height.toFloat()))-
                coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
        }
        val parent=displayedSize("media-video-frame");val frame=displayedSize("media-video-foreground")
        assertEquals(parent.y,frame.y,1.5f)
        assertEquals(4f/3f,frame.x/frame.y,.01f)
        compose.runOnIdle { assertEquals(1440,fixture.videoView!!.width);assertEquals(1080,fixture.videoView!!.height) }
    }
    @Test fun pausedStateAndPositionSurvivePlayerRemountWithoutConsumingAutoplayAgain() {
        val fixtures=mutableListOf<AudioPlayerFixture>();val playback=PlaybackState();val revision=mutableStateOf(0);val clip=file()
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalVideoPlayerFactory provides {
            AudioPlayerFixture(arrayOf(ExoPlayer::class.java)).also { fixtures+=it }.player as ExoPlayer
        }) { key(revision.value) { MediaVideo(clip,"视频",false,{},Modifier.size(300.dp,220.dp),playback=playback,autoPlay=true) } } } }
        change { fixtures.last().player.pause();fixtures.last().position=1000;revision.value++ }
        compose.runOnIdle { assertEquals(2,fixtures.size);assertFalse(fixtures.last().playing);assertEquals(1000,fixtures.last().position) }
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Before fun pauseClock() { compose.mainClock.autoAdvance=false }
    private fun change(action:()->Unit) { compose.runOnIdle(action);repeat(3) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() } }
    private fun file()=File(ApplicationProvider.getApplicationContext<Application>().cacheDir,"video-policy.mp4").apply { writeBytes(byteArrayOf(1)) }
    @Test fun invisibleAutoplayStartsWhenActivatedAndPictureTapPausesWithoutRestartingOnReturn() {
        val fixture=AudioPlayerFixture(arrayOf(ExoPlayer::class.java));val active=mutableStateOf(false);val playback=PlaybackState()
        val clip=file()
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalVideoPlayerFactory provides { fixture.player as ExoPlayer },LocalPageActive provides active.value) {
            MediaVideo(clip,"预览",false,{},Modifier.size(300.dp,220.dp),playback=playback,autoPlay=true)
        } } }
        change { assertFalse(fixture.playing);active.value=true }
        compose.runOnIdle { assertTrue(fixture.playing);assertEquals(SeekParameters.EXACT,fixture.seekParameters) }
        compose.onNodeWithTag("media-video-frame").performTouchInput { click(center) }
        change { assertFalse(fixture.playing);active.value=false }
        change { active.value=true }
        compose.runOnIdle { assertFalse("Manually paused video must stay paused",fixture.playing) }
        compose.onNodeWithTag("media-video-frame").performTouchInput { click(center) }
        change { assertTrue(fixture.playing);active.value=false }
        change { assertFalse(fixture.playing);active.value=true }
        compose.runOnIdle { assertTrue(fixture.playing) }
    }
    @Test fun livePhotoStartsAfterActivationStaysMountedOnPauseAndDoesNotAutoreplayAfterCompletion() {
        val fixture=AudioPlayerFixture(arrayOf(ExoPlayer::class.java));val active=mutableStateOf(false);val live=LivePhotoState()
        val clip=file()
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalVideoPlayerFactory provides { fixture.player as ExoPlayer },LocalPageActive provides active.value) {
            Box(Modifier.size(300.dp,220.dp)) { LivePhotoPlayback(clip,"实况",MediaScale.FIT,true,live) }
        } } }
        change { assertFalse(live.requested);active.value=true }
        change { assertTrue(live.requested);assertTrue(fixture.playing);assertEquals(0f,fixture.volume);fixture.firstFrame() }
        compose.onNodeWithTag("media-video-frame").performTouchInput { click(center) }
        change { assertTrue(live.requested);assertTrue(live.mounted);assertFalse(fixture.playing);active.value=false }
        change { active.value=true }
        change { assertTrue(live.requested);assertFalse(fixture.playing);fixture.end() }
        repeat(50) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
        compose.onNodeWithTag("play-motion-photo").assertExists()
        change { active.value=false }
        change { active.value=true }
        compose.runOnIdle { assertFalse(live.requested) }
        compose.onNodeWithTag("play-motion-photo").assertIsEnabled().performClick()
        repeat(6) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
        compose.runOnIdle { assertTrue(live.requested);assertTrue(fixture.playing) }
    }
    @Test fun leavingDuringScrubCancelsResumeAndSoundPolicyDoesNotResetManualMuteOnUnrelatedUpdates() {
        val fixture=AudioPlayerFixture(arrayOf(ExoPlayer::class.java));val active=mutableStateOf(true);val audible=mutableStateOf(true);val playback=PlaybackState()
        val clip=file()
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalVideoPlayerFactory provides { fixture.player as ExoPlayer },LocalPageActive provides active.value) {
            MediaVideo(clip,"视频",false,{},Modifier.size(300.dp,220.dp),playback=playback,autoPlay=true,playAudio=audible.value)
        } } }
        compose.onNodeWithContentDescription("切换声音").performClick()
        change { assertEquals(0f,fixture.volume);playback.seek(2000f);active.value=false }
        change { playback.finishScrub();assertFalse(fixture.playing);active.value=true }
        change { assertFalse(fixture.playing);assertEquals(0f,fixture.volume);audible.value=false }
        change { audible.value=true }
        compose.runOnIdle { assertEquals(1f,fixture.volume);assertFalse(playback.muted) }
    }
}
