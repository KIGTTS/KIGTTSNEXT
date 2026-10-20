package com.kigtts.next

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.text.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ContentPresentationTest {
    private lateinit var app:Application
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        listOf("media-presentation","playback","quick-phrases","appearance").forEach { app.getSharedPreferences(it,0).edit().clear().commit() }
    }
    @Test fun replacingMediaUsesOnePageAndCloseRestoresOriginalSubtitleAndFormatting() {
        val model=testAppModel(app)
        val text=TextFieldValue("回到之前的字幕",TextRange(2,5))
        model.text(text.text);model.editText(text)
        val first=model.mediaRepository.html("第一页","<p>第一</p>")
        val second=model.mediaRepository.html("第二页","<p>第二</p>")
        try {
            model.content.show(first,false);model.content.show(second)
            assertEquals(second.id,model.state.value.mediaId)
            model.content.closeContent();assertNull(model.state.value.mediaId);assertEquals(text,model.state.value.editorValue)
            model.content.closeContent();assertNull(model.state.value.mediaId);assertEquals(text,model.state.value.editorValue)
            val phrase=QuickPhrase("带格式的文字","常用",richText=true,formats=listOf(TextFormatRun(0,3,TextFormat.BOLD)))
            model.content.send(phrase);model.content.show(first);model.content.remove(first.id)
            assertNull(model.state.value.mediaId);assertEquals(phrase.text,model.state.value.editorText);assertEquals(phrase.formats,model.state.value.subtitleFormats)
        } finally { model.content.remove(first.id);model.content.remove(second.id) }
    }
    @Test fun autoPlaybackIsHostOwnedAndPerCardOptOutSurvivesEditingAndRestart() {
        val model=testAppModel(app)
        assertTrue(model.state.value.autoPlayback)
        model.toggleAutoPlayback();assertFalse(model.state.value.autoPlayback)
        assertFalse(testAppModel(app).state.value.autoPlayback)
        val page=model.mediaRepository.html("页面","<p>页面</p>")
        try {
            val phrase=QuickPhrase("正文","常用",mediaId=page.id,type=QuickCardType.PAGE,autoPlay=false)
            model.content.beginPhrase(phrase,"常用");assertFalse(model.content.phraseDraft.value!!.autoPlay)
            model.content.savePhrase()
            val restored=QuickTextLibrary(QuickPhraseStore(app)).phrases.value.last()
            assertFalse(restored.autoPlay)
            model.content.send(restored);assertFalse(model.state.value.contentAutoPlay)
            assertTrue(QuickPhraseCodec.read(org.json.JSONObject().put("text","旧条目").put("category","常用")).autoPlay)
        } finally { model.content.remove(page.id) }
    }
    @Test fun largerPreferredAndRichSizesPersistWithoutChangingText() {
        val store=SubtitleSizeStore(app);store.save(45f);assertEquals(45f,SubtitleSizeStore(app).load())
        val phrase=QuickPhrase("巨大字号","常用",richText=true,formats=TextFormatting.apply(emptyList(),4,0,4,TextFormat.SIZE,400))
        assertTrue(QuickTextLibrary(QuickPhraseStore(app)).savePhrase(phrase))
        assertEquals(400L,QuickTextLibrary(QuickPhraseStore(app)).phrases.value.last().formats.single().value)
        assertEquals("巨大字号",formattedText(phrase.text,phrase.formats).text)
    }
    @Test fun pickerDraftRestoresItsStableIdentityTypesFormattingAndEachSelectedResource() {
        val model=testAppModel(app)
        val phrase=QuickPhrase("尚未保存的正文","常用",title="标题",mediaId="photo",type=QuickCardType.MEDIA,autoPlay=false,
            richText=true,formats=listOf(TextFormatRun(1,4,TextFormat.SIZE,300)),colorMode=CardColorMode.CUSTOM,color=0xFFCCAA11)
        model.content.beginPhrase(phrase,"常用")
        model.content.editPhrase { it.switchType(QuickCardType.AUDIO).attach(QuickCardType.AUDIO,"sound") }
        val original=model.content.phraseDraft.value!!
        val saved=model.content.savedPhraseDraft()!!
        model.content.cancelPhrase();assertTrue(model.content.restorePhraseDraft(saved))
        assertEquals(original,model.content.phraseDraft.value)
        assertFalse(model.content.restorePhraseDraft(saved))
        model.content.editPhrase { it.switchType(QuickCardType.MEDIA) };assertEquals("photo",model.content.phraseDraft.value!!.mediaId)
    }
}
