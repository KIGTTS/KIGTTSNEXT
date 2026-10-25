package com.kigtts.next.media

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.AppCommand
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class BrowserInputTest {
    private class Page(context: Context) : WebView(context) {
        init { measure(MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY)); layout(0, 0, 300, 400); left = 0; top = 0; right = 300; bottom = 400 }
        val touches = mutableListOf<Triple<Int, Float, Float>>()
        val keys = mutableListOf<KeyEvent>()
        override fun dispatchTouchEvent(event: MotionEvent): Boolean { touches += Triple(event.actionMasked, event.x, event.y); return true }
        override fun dispatchKeyEvent(event: KeyEvent): Boolean { keys += event; return true }
    }
    @Test fun nativeDragFollowsPathAndRejectsHostAreaBeforeSendingAnyTouch() = runTest {
        val page = Page(ApplicationProvider.getApplicationContext())
        try {
            val bad = AppCommand.BrowserSession("drag", text = """{"points":[[100,120],[100,19]],"durationMs":160}""")
            assertTrue(runCatching { BrowserInput.perform(page, bad, 0, 20) }.isFailure); assertTrue(page.touches.isEmpty())
            BrowserInput.perform(page, bad.copy(text = """{"points":[[100,120],[140,180],[200,220]],"durationMs":160}"""), 0, 20)
            assertEquals(Triple(MotionEvent.ACTION_DOWN, 100f, 100f), page.touches.first())
            assertEquals(Triple(MotionEvent.ACTION_UP, 200f, 200f), page.touches.last())
            assertTrue(page.touches.count { it.first == MotionEvent.ACTION_MOVE } >= 8)
        } finally { page.destroy() }
    }
    @Test fun keyboardShortcutsReachOnlyTheWebViewAndCancelledPressReleasesPointer() = runTest {
        val page = Page(ApplicationProvider.getApplicationContext())
        try {
            BrowserInput.perform(page, AppCommand.BrowserSession("key", text = "CTRL+A"))
            assertEquals(2, page.keys.size); assertEquals(KeyEvent.KEYCODE_A, page.keys.first().keyCode); assertTrue(page.keys.first().isCtrlPressed)
            val job = launch { BrowserInput.perform(page, AppCommand.BrowserSession("long-press", x = 50f, y = 50f)) }
            testScheduler.runCurrent(); job.cancelAndJoin()
            assertEquals(MotionEvent.ACTION_CANCEL, page.touches.last().first)
        } finally { page.destroy() }
    }
    @Test fun exportExactThemeAndCanvasScriptsForBrowserRegression() {
        val directory = java.io.File("build/browser-fixtures").apply { mkdirs() }
        java.io.File(directory, "scripts.json").writeText(org.json.JSONObject()
            .put("dark", WebTheme(true, mapOf("primary" to "#aaffdd", "surface" to "#101819", "on-surface" to "#eeeeee")).script())
            .put("light", WebTheme(false, mapOf("primary" to "#00696e", "surface" to "#f4fafb", "on-surface" to "#171d1e")).script())
            .put("canvas", BrowserCanvasCapture.script).toString())
    }
}
