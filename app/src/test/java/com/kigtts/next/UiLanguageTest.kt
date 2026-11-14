package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.i18n.UiLanguage
import com.kigtts.next.i18n.UiLanguageStore
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.QuickPhraseStore
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
}
