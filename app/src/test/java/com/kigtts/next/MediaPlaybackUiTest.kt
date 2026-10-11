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
