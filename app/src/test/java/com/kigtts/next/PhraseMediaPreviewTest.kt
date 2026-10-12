@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.PhraseDetailsForm
import com.kigtts.next.ui.media.LocalVideoPlayerFactory
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class PhraseMediaPreviewTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun previewControlsAreOutsideVideoAndSoundSelectionPersistsWithoutSendingSubtitle() {
        val app=ApplicationProvider.getApplicationContext<Application>();app.getSharedPreferences("media-presentation",0).edit().clear().commit()
        val model=testAppModel(app);val fixture=AudioPlayerFixture(arrayOf(ExoPlayer::class.java))
        val entry=model.mediaRepository.import(Uri.fromFile(File(app.cacheDir,"editor-video.mp4").apply { writeBytes(byteArrayOf(1)) }),"video/mp4")
        model.content.beginPhrase(QuickPhrase("共享字幕","常用",mediaId=entry.id,type=QuickCardType.MEDIA),"常用")
        try {
            compose.setContent { MaterialTheme { CompositionLocalProvider(LocalVideoPlayerFactory provides { fixture.player as ExoPlayer }) {
                val draft by model.content.phraseDraft.collectAsState()
                draft?.let { PhraseDetailsForm(it,listOf("常用"),model,true) }
            } } }
            compose.onNodeWithTag("phrase-media-preview").performScrollTo()
            compose.onNodeWithText(entry.name).assertDoesNotExist()
            compose.onNodeWithContentDescription(entry.name).assertDoesNotExist()
            val picture=compose.onNodeWithTag("phrase-media-preview").getUnclippedBoundsInRoot()
            val controls=compose.onNodeWithTag("phrase-preview-controls").getUnclippedBoundsInRoot()
            assertTrue(controls.top>=picture.bottom);assertFalse(fixture.playing)
            compose.onNodeWithContentDescription("音频播放：自动播放").assertIsSelected()
            compose.onNodeWithContentDescription("音频播放：不播放").performScrollTo().performClick()
            compose.onNodeWithContentDescription("音频播放：跟随朗读设置").performClick()
            assertEquals(MediaAudioMode.FOLLOW_READING,model.content.phraseDraft.value!!.audioMode)
            assertNull(model.state.value.mediaId)
            compose.onRoot().captureRoboImage("build/previews/phrase-editor-media-policy.png")
            compose.onNodeWithTag("phrase-resource-more").performScrollTo().performClick()
            compose.onNodeWithTag("detach-phrase-media").performClick()
            compose.waitForIdle();assertNull(model.content.phraseDraft.value!!.mediaId)
            compose.onNodeWithTag("phrase-media-preview").assertDoesNotExist()
        } finally { model.content.cancelPhrase();model.content.remove(entry.id) }
    }
}
