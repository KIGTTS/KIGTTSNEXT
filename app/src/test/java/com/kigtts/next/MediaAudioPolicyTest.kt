package com.kigtts.next

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MediaAudioPolicyTest {
    @Test fun endedVideoRestartsWithOneTapEvenWhenPlayIntentRemainsTrue() {
        val fixture=AudioPlayerFixture().apply { state=androidx.media3.common.Player.STATE_ENDED;playWhenReady=true }
        com.kigtts.next.ui.media.PlaybackState().apply { player=fixture.player }.toggle()
        assertEquals(listOf(0L),fixture.seeks);assertTrue(fixture.playing)
    }
    @Test fun scrubbingAnEndedVideoDoesNotTurnItIntoAutomaticPlayback() {
        val fixture=AudioPlayerFixture().apply { state=androidx.media3.common.Player.STATE_ENDED;playWhenReady=true }
        com.kigtts.next.ui.media.PlaybackState().apply { player=fixture.player;duration=3000f;seek(1000f);finishScrub() }
        assertEquals(listOf(1000L),fixture.seeks);assertFalse(fixture.playing)
    }
    @Test fun defaultsAndFollowReadingAreIndependentOfVisualAutoplay() {
        assertEquals(MediaAudioMode.AUTOMATIC,MediaAudioMode.defaultFor(MediaKind.VIDEO))
        assertEquals(MediaAudioMode.SILENT,MediaAudioMode.defaultFor(MediaKind.MOTION_PHOTO))
        assertTrue(MediaAudioMode.AUTOMATIC.audible(false));assertFalse(MediaAudioMode.SILENT.audible(true))
        assertTrue(MediaAudioMode.FOLLOW_READING.audible(true));assertFalse(MediaAudioMode.FOLLOW_READING.audible(false))
        val legacy=org.json.JSONObject().put("text","字幕").put("category","常用").put("audioMode","unknown")
        assertNull(QuickPhraseCodec.read(legacy).audioMode)
    }
    @Test fun modesSurviveLibraryDraftHistoryAndRestartIncludingExplicitNoAutoplay() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        listOf("quick-phrases","media-presentation").forEach { app.getSharedPreferences(it,0).edit().clear().commit() }
        val model=testAppModel(app)
        val initial=model.state.value
        val video=model.mediaRepository.import(Uri.fromFile(File(app.cacheDir,"mode.mp4").apply { writeBytes(byteArrayOf(1)) }),"video/mp4")
        try {
            val first=QuickPhrase("字幕","常用",mediaId=video.id,type=QuickCardType.MEDIA,autoPlay=false,audioMode=MediaAudioMode.FOLLOW_READING)
            model.content.beginPhrase(first,"常用")
            val snapshot=model.content.savedPhraseDraft()!!;model.content.cancelPhrase();assertTrue(model.content.restorePhraseDraft(snapshot))
            assertEquals(first.audioMode,model.content.phraseDraft.value!!.audioMode);model.content.savePhrase()
            assertEquals(first.audioMode,QuickTextLibrary(QuickPhraseStore(app)).phrases.value.first { it.id==first.id }.audioMode)
            model.content.send(first)
            assertFalse(model.state.value.contentAutoPlay);assertEquals(first.audioMode,model.state.value.contentAudioMode)
            val restored=testAppModel(app).state.value
            assertFalse(restored.contentAutoPlay);assertEquals(first.audioMode,restored.contentAudioMode)
            assertNull(restored.mediaId)
            model.content.show(video,true,MediaAudioMode.AUTOMATIC);model.content.closeContent()
            assertNull(model.state.value.mediaId)
            assertEquals(initial.contentAutoPlay,model.state.value.contentAutoPlay);assertEquals(initial.contentAudioMode,model.state.value.contentAudioMode)
        } finally { model.content.remove(video.id) }
    }
}
