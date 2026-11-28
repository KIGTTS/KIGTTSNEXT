package com.kigtts.next

import android.net.Uri
import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.audio.AudioCardSession
import com.kigtts.next.state.*
import com.kigtts.next.ui.media.AudioCardControls
import kotlinx.coroutines.*
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class AudioCardSessionTest {
    @Test fun migratedAudioPlaysItsNonDestructiveClipAndRestartKeepsTheRange() {
        val fixture = AudioPlayerFixture()
        val session = AudioCardSession { fixture.player }
        session.start("legacy", Uri.parse("file:///legacy.wav"), startMs = 200, endMs = 800)
        assertEquals(200L, fixture.item!!.clippingConfiguration.startPositionMs)
        assertEquals(800L, fixture.item!!.clippingConfiguration.endPositionMs)
        session.restart()
        assertEquals(200L, fixture.item!!.clippingConfiguration.startPositionMs)
        assertEquals(1, fixture.prepares)
    }
    @Test fun audioUsesTheSharedCaptionAndBackgroundDeletionStopsOnMainWithoutClearingIt() {
        compose.mainClock.autoAdvance=false
        val app=ApplicationProvider.getApplicationContext<Application>()
        val model=testAppModel(app)
        val fixture=AudioPlayerFixture();val session=AudioCardSession { fixture.player }
        var state=AppState(autoPlayback = true)
        var stoppedOnMain=false
        val workspace=ContentWorkspace(app,model.mediaRepository,QuickTextLibrary(QuickPhraseStore(app)),
            { state },{ state=it(state) },{},{},{ state=state.copy(editorValue=androidx.compose.ui.text.input.TextFieldValue(it)) },
            playAudio={ entry,auto -> state=state.copy(audioId=entry.id);session.start(entry.id,Uri.fromFile(model.mediaRepository.file(entry)!!),auto) },
            stopAudio={ check(Looper.myLooper()==Looper.getMainLooper());stoppedOnMain=true;session.clear() })
        val file=File(app.cacheDir,"threaded-audio.wav").apply { writeBytes(byteArrayOf(1,2,3,4)) }
        val entry=model.mediaRepository.import(Uri.fromFile(file),"audio/wav")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        compose.setContent { MaterialTheme { AudioCardControls(session, androidx.compose.ui.Modifier.fillMaxWidth()) } }
        val collapsed=compose.onNodeWithTag("media-playback-surface").getUnclippedBoundsInRoot()
        val area=compose.onNodeWithTag("audio-card-controls").getUnclippedBoundsInRoot()
        assertEquals("Replay pill is centered like live photo playback",((area.left+area.right)/2).value,((collapsed.left+collapsed.right)/2).value,.5f)
        try {
            compose.runOnIdle {
                val formats=listOf(com.kigtts.next.text.TextFormatRun(0,2,com.kigtts.next.text.TextFormat.UNDERLINE))
                workspace.send(QuickPhrase("音频条目的真实字幕","常用",mediaId=entry.id,type=QuickCardType.AUDIO,richText=true,formats=formats))
                assertEquals("音频条目的真实字幕",state.editorValue.text)
                assertEquals(formats,state.subtitleFormats)
                workspace.closeContent();assertNull(state.audioId);assertEquals("音频条目的真实字幕",state.editorValue.text)
                workspace.show(entry);stoppedOnMain=false
            }
            assertTrue(fixture.playing)
            val deletion=scope.async { workspace.removeAsync(entry.id) }
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
                deletion.isCompleted
            }
            runBlocking { deletion.await() }
            assertTrue(stoppedOnMain);assertFalse(fixture.playing);assertFalse(session.expanded)
            assertEquals("音频条目的真实字幕",state.editorValue.text);assertNull(state.audioId);assertNull(model.mediaRepository.get(entry.id))
        } finally {
            scope.cancel();compose.runOnIdle { session.release() };model.mediaRepository.remove(entry.id);file.delete();compose.mainClock.autoAdvance=true
        }
    }
    @Test fun preparedPreviewAndDisabledAutoplayStaySilentUntilManuallyStarted() {
        val fixture=AudioPlayerFixture();val session=com.kigtts.next.audio.AudioCardSession { fixture.player }
        session.start("preview",android.net.Uri.parse("file:///preview.wav"),autoPlay=false)
        assertEquals(1,fixture.prepares);assertFalse(fixture.playing);assertFalse(session.expanded)
        session.restart();assertTrue(fixture.playing);assertTrue(session.expanded);assertEquals(1,fixture.prepares)
        session.release();assertEquals(1,fixture.released)
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun completionMorphsControlsIntoReplayPillAndReusesTheSamePlayer() {
        val fixture = AudioPlayerFixture(); var created = 0
        val session = AudioCardSession { created++; fixture.player }
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { AudioCardControls(session) } }
        compose.runOnIdle { session.start("audio", Uri.parse("file:///test.wav")) }
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        assertTrue("Player must remain active: expanded=${session.expanded}, state=${fixture.state}; ${compose.onRoot().printToString()}", session.playback.playing)
        compose.onNodeWithContentDescription("暂停").assertExists()
        assertEquals(1, created); assertTrue(session.expanded); assertEquals(3000f, session.playback.duration)
        val before = compose.onNodeWithTag("audio-card-controls").getUnclippedBoundsInRoot()
        compose.runOnIdle { fixture.end() }; compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        val middle = compose.onNodeWithTag("audio-card-controls").getUnclippedBoundsInRoot()
        compose.mainClock.advanceTimeBy(1200); compose.waitForIdle()
        val after = compose.onNodeWithTag("audio-card-controls").getUnclippedBoundsInRoot()
        assertTrue((before.bottom - before.top).value > (middle.bottom - middle.top).value)
        assertTrue((middle.bottom - middle.top).value > (after.bottom - after.top).value)
        compose.onNodeWithTag("play-card-audio").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        assertTrue(session.expanded); assertTrue(fixture.playing); assertEquals(0L, fixture.position)
        assertEquals(1, created); assertEquals(1, fixture.prepares)
        compose.runOnIdle { session.pause(); session.collapse(); session.release() }
        assertFalse(fixture.playing); assertEquals(1, fixture.released)
        compose.mainClock.autoAdvance = true
    }
}
