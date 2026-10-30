package com.kigtts.next

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.text.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickCardContentTest {
    private lateinit var app: Application
    @Before fun setup() { app = ApplicationProvider.getApplicationContext(); app.getSharedPreferences("quick-phrases", 0).edit().clear().commit() }
    @Test fun everyCardTypeColorAndRichFormatSurvivesSortingEditingAndRestart() {
        val library = QuickTextLibrary(QuickPhraseStore(app))
        QuickCardType.entries.forEachIndexed { i, type ->
            assertTrue(library.savePhrase(QuickPhrase("完整正文", "常用", "card-$i", "缩略标题", if (type == QuickCardType.TEXT) null else "asset-$i",
                type, listOf(CardColorMode.DEFAULT, CardColorMode.CUSTOM)[i % 2], 0xFF835FC1, type == QuickCardType.TEXT, listOf(TextFormatRun(0, 2, TextFormat.BOLD)))))
        }
        val original = library.phrases.value.takeLast(4)
        library.movePhrase(original[3].id, original[0].id)
        library.edit(original[0].id, "修改正文", "", "常用")
        val restored = QuickTextLibrary(QuickPhraseStore(app))
        assertEquals(library.phrases.value, restored.phrases.value)
        assertEquals(CardColorMode.CUSTOM, restored.phrases.value.first { it.id == "card-1" }.colorMode)
        assertEquals(CardColorMode.DEFAULT, restored.phrases.value.first { it.id == "card-2" }.colorMode)
        assertEquals("", restored.phrases.value.first { it.id == "card-0" }.title)
        assertTrue(QuickPhrase("正文", "常用").richText)
    }
    @Test fun legacyNoColorUsesDefaultWithoutLosingOtherCardFields() {
        val source = QuickPhrase("正文", "常用", title = "标题", colorMode = CardColorMode.NONE)
        assertEquals(source.copy(colorMode = CardColorMode.DEFAULT), QuickPhraseCodec.read(QuickPhraseCodec.write(source)))
    }
    @Test fun draftTabsPreserveEachResourceAndCancelledImportsNeverAttachToAnotherDraft() = runBlocking {
        val model = testAppModel(app)
        val image = File(app.cacheDir, "direct-card.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val audio = File(app.cacheDir, "direct-card.wav").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        model.content.beginPhrase(null, "常用"); val key = model.content.phraseDraft.value!!.key
        model.content.editPhrase { it.copy(text="所有类型共有的字幕",title="共有标题") }
        model.content.editPhrase { it.switchType(QuickCardType.MEDIA) }
        model.content.importPhrase(key, QuickCardType.MEDIA, Uri.fromFile(image))
        val mediaId = model.content.phraseDraft.value!!.mediaId!!
        model.content.editPhrase { it.switchType(QuickCardType.AUDIO) }
        assertNull(model.content.phraseDraft.value!!.mediaId)
        model.content.importPhrase(key, QuickCardType.AUDIO, Uri.fromFile(audio))
        val audioId = model.content.phraseDraft.value!!.mediaId!!
        assertEquals("所有类型共有的字幕",model.content.phraseDraft.value!!.text)
        assertEquals("共有标题",model.content.phraseDraft.value!!.title)
        assertEquals(MediaKind.AUDIO, model.mediaRepository.get(audioId)!!.kind)
        assertArrayEquals(audio.readBytes(), model.mediaRepository.file(model.mediaRepository.get(audioId)!!)!!.readBytes())
        model.content.editPhrase { it.switchType(QuickCardType.MEDIA) }; assertEquals(mediaId, model.content.phraseDraft.value!!.mediaId)
        model.content.editPhrase { it.switchType(QuickCardType.AUDIO) }; assertEquals(audioId, model.content.phraseDraft.value!!.mediaId)
        model.content.editPhrase { it.switchType(QuickCardType.TEXT) }; assertNull(model.content.phraseDraft.value!!.mediaId)
        assertTrue(model.content.phraseDraft.value!!.valid(model.mediaRepository))
        val before = model.mediaRepository.entries.value.map { it.id }.toSet()
        model.content.beginPhrase(null, "常用")
        model.content.importPhrase(key, QuickCardType.MEDIA, Uri.fromFile(image))
        assertEquals(before, model.mediaRepository.entries.value.map { it.id }.toSet())
        assertNull(model.content.phraseDraft.value!!.mediaId)
        before.forEach { model.content.remove(it) }
    }
    @Test fun oldHtmlReferencesOpenAsPageAndRichTextSendingKeepsPlainSpeechContent() {
        val model = testAppModel(app); val html = model.mediaRepository.html("网页", "<p>内容</p>")
        val legacy = QuickPhrase("正文", "常用", mediaId = html.id)
        model.content.beginPhrase(legacy, "常用"); assertEquals(QuickCardType.PAGE, model.content.phraseDraft.value!!.type)
        val formatted = QuickPhrase("你好，朋友", "常用", title = "打招呼", type = QuickCardType.TEXT, richText = true,
            formats = listOf(TextFormatRun(0, 2, TextFormat.BOLD), TextFormatRun(3, 5, TextFormat.COLOR, 0xFF6750A4)))
        model.content.send(formatted)
        assertEquals(formatted.text, model.context().editorText)
        assertEquals(formatted.formats, model.state.value.subtitleFormats)
        model.text("清空格式"); assertTrue(model.state.value.subtitleFormats.isEmpty())
        model.content.remove(html.id)
    }
    @Test fun underlineTogglesKeepsOtherStylesAndSurvivesDraftAndLibrarySerialization() {
        val text="你好朋友"
        val bold=TextFormatting.apply(emptyList(),4,0,4,TextFormat.BOLD)
        val underlined=TextFormatting.apply(bold,4,0,4,TextFormat.UNDERLINE)
        assertTrue(formattedText(text,underlined).spanStyles.any { it.item.textDecoration==androidx.compose.ui.text.style.TextDecoration.Underline })
        val cleared=TextFormatting.apply(underlined,4,1,3,TextFormat.UNDERLINE)
        assertEquals(listOf(0 to 1,3 to 4),cleared.filter { it.kind==TextFormat.UNDERLINE }.map { it.start to it.end })
        assertTrue(cleared.any { it.kind==TextFormat.BOLD&&it.start==0&&it.end==4 })
        val legacy=listOf(TextFormatRun(0,4,TextFormat.SIZE,400))
        assertEquals(legacy,TextFormatting.normalize(legacy,4))
        assertTrue(TextFormatting.apply(legacy,4,0,4,TextFormat.SIZE,100).isEmpty())
        val phrase=QuickPhrase(text,"常用",type=QuickCardType.AUDIO,richText=true,formats=underlined)
        assertEquals(underlined,QuickPhraseCodec.read(QuickPhraseCodec.write(phrase)).formats)
        val draft=PhraseDraft("draft",phrase.id,text,"","常用",null,type=QuickCardType.AUDIO,richText=true,formats=underlined)
        assertEquals(underlined,PhraseDraftState.read(PhraseDraftState.write(draft))!!.formats)
    }
    @Test fun audioAndVideoDisplayUserContentInsteadOfFilenameOrPath() {
        com.kigtts.next.media.MediaKind.entries.filter { it in setOf(MediaKind.AUDIO,MediaKind.VIDEO) }.forEach { kind->
            val entry=MediaEntry("asset","/private/resources/secret-file.mp4",kind,"file.mp4")
            assertEquals(kind.label,com.kigtts.next.ui.media.mediaDisplayTitle(entry,emptyList()))
            val caption=QuickPhrase("用户的字幕\n第二行","常用",mediaId=entry.id,type=QuickCardType.MEDIA)
            assertEquals("用户的字幕",com.kigtts.next.ui.media.mediaDisplayTitle(entry,listOf(caption)))
            assertEquals("用户的标题",com.kigtts.next.ui.media.mediaDisplayTitle(entry,listOf(caption.copy(title="用户的标题"))))
        }
    }
    @Test fun formattingTracksInsertionDeletionAndIndependentSelectionStyles() {
        val original = "你好朋友"; val bold = TextFormatting.apply(emptyList(), 4, 0, 2, TextFormat.BOLD)
        val inserted = TextFormatting.rebase(bold, original, "你呀好朋友")
        assertEquals(listOf(TextFormatRun(0, 3, TextFormat.BOLD)), inserted)
        assertTrue(TextFormatting.rebase(bold, original, "朋友").isEmpty())
        val italic = TextFormatting.apply(inserted, 5, 1, 3, TextFormat.ITALIC)
        val toggled = TextFormatting.apply(italic, 5, 0, 3, TextFormat.BOLD)
        assertEquals(listOf(TextFormatRun(1, 3, TextFormat.ITALIC)), toggled)
        val color = TextFormatting.apply(toggled, 5, 1, 3, TextFormat.COLOR, 0x12123456)
        assertEquals(0xFF123456L, color.last().value)
        assertEquals("你呀好朋友", formattedText("你呀好朋友", color).text)
    }
}
