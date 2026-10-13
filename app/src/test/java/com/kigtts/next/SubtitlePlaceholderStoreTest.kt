package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.SubtitlePlaceholder
import com.kigtts.next.state.SubtitlePlaceholderStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitlePlaceholderStoreTest {
    private fun application() = ApplicationProvider.getApplicationContext<Application>().also {
        it.getSharedPreferences("appearance", 0).edit().clear().commit()
    }
    @Test fun unspecifiedHintUsesDefaultWithoutWritingItIntoPreferences() {
        val app = application(); val store = SubtitlePlaceholderStore(app)
        assertEquals(SubtitlePlaceholder.DEFAULT, store.load())
        assertFalse(app.getSharedPreferences("appearance", 0).contains("subtitlePlaceholder"))
    }
    @Test fun customMultilineAndExplicitEmptyHintsSurviveRestart() {
        val app = application(); SubtitlePlaceholderStore(app).save("想说的话\n点此输入")
        assertEquals("想说的话\n点此输入", SubtitlePlaceholderStore(app).load())
        SubtitlePlaceholderStore(app).save("")
        assertEquals("", SubtitlePlaceholderStore(app).load())
    }
    @Test fun unicodeLimitDoesNotSplitEmojiAndNormalizesNewlinesOnly() {
        val emoji = "😀".repeat(220)
        val result = SubtitlePlaceholder.normalize(emoji)
        assertEquals(200, result.codePointCount(0, result.length))
        assertEquals("😀".repeat(200), result)
        assertEquals(" 你好\n世界 ", SubtitlePlaceholder.normalize(" 你好\r\n世界\u0000 "))
    }
    @Test fun localizedDefaultsCanChangeWhileCustomHintsRemainUntouched() {
        val app = application()
        assertEquals("Tap to type", SubtitlePlaceholderStore(app, "Tap to type").load())
        SubtitlePlaceholderStore(app).save("我的提示")
        assertEquals("我的提示", SubtitlePlaceholderStore(app, "Tap to type").load())
    }
}
