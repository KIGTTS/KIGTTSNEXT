package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.i18n.UiLanguage
import com.kigtts.next.i18n.UiLanguageStore
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.QuickPhraseStore
import com.kigtts.next.ui.settings.searchSettings
import com.kigtts.next.ui.settings.settingsCards
import com.kigtts.plugin.api.PluginContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UiLanguageTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun standardIsDefaultAndTonePersistsWithoutChangingPresets() {
        val language = UiLanguageStore(app)
        assertEquals("zh-CN", language.load())
        assertEquals("翻转字幕", app.uiCopy("subtitle.flip"))
        val phrases = QuickPhraseStore(app)
        val first = phrases.load()
        assertEquals(listOf("展会常用", "合影集邮", "后勤求助", "扩列社交"), phrases.loadCategories(first))
        assertTrue(first.any { it.text == "你好呀！很高兴认识你~" })
        language.save(UiLanguage.CASUAL)
        assertEquals("zh-CN-x-casual", UiLanguageStore(app).load())
        assertEquals("把字幕倒过来", app.uiCopy("subtitle.flip"))
        assertEquals(first.map { it.id to it.text }, phrases.load().map { it.id to it.text })
        assertTrue(app.uiCopy("camera.weather", "service" to "天气源").contains("天气源"))
    }

    @Test fun englishAndKoreanCoverTheCatalogAndKeepNamedArguments() {
        fun catalog(tag: String): Map<String, String> {
            val json = app.assets.open("i18n/$tag.json").bufferedReader().use { JSONObject(it.readText()) }
            return json.keys().asSequence().associateWith(json::getString)
        }
        fun arguments(text: String) = Regex("\\{([a-zA-Z0-9_.-]+)\\}").findAll(text).map { it.groupValues[1] }.toSet()
        val standard = catalog("zh-CN")
        for ((locale, caption) in listOf(UiLanguage.ENGLISH to "Flip captions", UiLanguage.KOREAN to "자막 뒤집기", UiLanguage.JAPANESE to "字幕を反転")) {
            val translated = catalog(locale.tag)
            assertEquals(standard.keys, translated.keys)
            for ((key, text) in translated) {
                assertTrue(key, text.isNotBlank())
                assertEquals(key, arguments(standard.getValue(key)), arguments(text))
            }
            UiLanguageStore(app).save(locale)
            assertEquals(locale.tag, UiLanguageStore(app).load())
            assertEquals(caption, app.uiCopy("subtitle.flip"))
            assertTrue(app.uiCopy("camera.weather", "service" to "{provider}").contains("{provider}"))
        }
        assertEquals(UiLanguage.ENGLISH, UiLanguage.fromTag("en-US"))
        assertEquals(UiLanguage.KOREAN, UiLanguage.fromTag("ko-KR"))
        assertEquals(UiLanguage.JAPANESE, UiLanguage.fromTag("ja-JP"))
        assertEquals(UiLanguage.STANDARD, UiLanguage.fromTag("de-DE"))
        assertEquals("Quick Text", catalog("en").getValue("quick.dock"))
        assertEquals("Quick Text · Hold to talk", catalog("en").getValue("quick.dock_voice"))
    }

    @Test fun switchingLanguageUpdatesTheHostAndPluginContextWhilePreservingUserText() {
        val model = testAppModel(app)
        model.setUiLanguage(UiLanguage.ENGLISH)
        assertEquals("Tap to enter text", model.state.value.subtitlePlaceholder)
        assertEquals("en", model.context().locale)
        model.setSubtitlePlaceholder("自定义。")
        model.setUiLanguage(UiLanguage.KOREAN)
        assertEquals("自定义。", model.state.value.subtitlePlaceholder)
        assertEquals("ko", model.context().locale)
        assertEquals("ko", UiLanguageStore(app).load())

        fun copy(key: String) = app.uiCopy(key, locale = "ko")
        val cards = settingsCards(null, PluginContext("", "", locale = "ko")) { key, _ -> copy(key) }
        assertFalse(cards.flatMap { it.links }.any { it.id == "ui-language" })
        assertTrue(searchSettings("언어", cards, ::copy).any { it.path == "설정 / 모양 / 앱 언어" })
        assertTrue(searchSettings("배경 이미지", cards, ::copy).any { it.target == "배경 이미지" })
    }
}
