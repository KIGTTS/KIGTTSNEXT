package com.kigtts.next

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.WebPagePresentation
import com.kigtts.next.media.WebPresentationSession
import com.kigtts.next.state.AppState
import com.kigtts.next.state.Destination
import com.kigtts.next.state.QuickCardType
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.theme.AppearanceAssets
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.theme.ThemeSettings
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebPresentationSessionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun appearanceIsTemporaryAndThePageMustStillBeActiveAfterPermission() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var current: WebPagePresentation? = null
        val session = WebPresentationSession(context, "web", "phrase", null, this) { _, next -> current = next }
        val data = JSONObject("""{"appearance":{"themeColor":"#038387","mode":"dark","pureBlackDark":true},"controls":{"fadeDockWhenIdle":true,"fadeControlsWhenIdle":true}}""")
        assertFalse(session.apply(data, false, { true }) { false })
        assertNull(current)
        assertFalse(session.apply(data, false, { true }) { session.clear(); true })
        assertNull(current)
        assertTrue(session.apply(data, false, { true }) { true })
        val page = requireNotNull(current)
        val state = AppState(mediaId = "web", webPermissionEntryId = "phrase")
        assertTrue(page.matches(state))
        assertFalse(page.matches(state.copy(webPermissionEntryId = "next")))
        assertFalse(page.matches(state.copy(destination = Destination.Settings)))
        val base = ThemeSettings(mode = ThemeMode.LIGHT)
        val rendered = page.appearance.applyTo(base)
        assertEquals(ThemeMode.LIGHT, base.mode)
        assertEquals(ThemeMode.DARK, rendered.mode)
        assertEquals(0xff038387L, rendered.seed)
        assertTrue(rendered.deeperDark)
        assertTrue(page.chrome.fadeDockWhenIdle)
        assertTrue(page.fadeControlsWhenIdle)
        session.clear()
        assertNull(current)
    }

    @Test fun fullscreenUsesTheCurrentShortcutAndClearsOnlyItsOwnSession() = runBlocking {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val page = model.mediaRepository.web("Example", "https://example.com/page")
        val phrase = QuickPhrase("Example", "QA", mediaId = page.id, type = QuickCardType.PAGE)
        model.content.send(phrase)
        val session = WebPresentationSession(model.getApplication(), page.id, phrase.id, null, this,
            { owner, enabled -> model.setWebFullscreen(owner, page.id, phrase.id, enabled) }, model::updateWebPresentation)

        assertFalse(session.fullscreen(true, { true }) { false })
        assertFalse(session.fullscreen(true, { true }) { session.clear(); true })
        assertTrue(session.fullscreen(true, { true }) { true })
        assertTrue(model.state.value.fullscreen)
        assertTrue(session.fullscreen(false, { true }) { error("Exiting needs no new grant") })
        assertFalse(model.state.value.fullscreen)

        assertTrue(session.fullscreen(true, { true }) { true })
        model.content.send(phrase.copy(id = "other"))
        assertFalse(model.state.value.fullscreen)
        assertFalse(session.fullscreen(true, { true }) { true })

        model.content.send(phrase)
        model.enterSubtitleFullscreen()
        session.clear()
        assertTrue(model.state.value.fullscreen)
        model.exitSubtitleFullscreen()
        assertTrue(session.fullscreen(true, { true }) { true })
        session.clear()
        assertFalse(model.state.value.fullscreen)
    }

    @Test fun controlsAreOffByDefaultAndCanBeChangedIndependently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var current: WebPagePresentation? = null
        val session = WebPresentationSession(context, "web", "phrase", null, this) { _, next -> current = next }
        val theme = JSONObject("""{"appearance":{"themeColor":"#038387"}}""")
        assertTrue(session.apply(theme, false, { true }) { true })
        val original = requireNotNull(current)
        assertFalse(original.fadeControlsWhenIdle)
        assertFalse(original.chrome.fadeHeaderWhenIdle)
        assertFalse(original.chrome.fadeDockWhenIdle)

        val on = JSONObject("""{"fadeControlsWhenIdle":true,"fadeHeaderWhenIdle":true}""")
        val off = JSONObject("""{"fadeControlsWhenIdle":false}""")
        assertFalse(session.setControls(on, { true }) { false })
        assertEquals(original, current)
        assertTrue(session.setControls(on, { true }) { true })
        assertTrue(requireNotNull(current).fadeControlsWhenIdle)
        assertTrue(session.setControls(off, { true }) { error("Disabling must not request permission") })
        assertFalse(requireNotNull(current).fadeControlsWhenIdle)
        assertTrue(requireNotNull(current).chrome.fadeHeaderWhenIdle)
        assertEquals(original.appearance, current?.appearance)
        assertFalse(session.setControls(on, { true }) { session.clear(); true })
        assertNull(current)
    }

    @Test fun aLocalImageIsReadOnlyFromTheOwningProject() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = folder.newFolder()
        val image = File(project, "background.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var current: WebPagePresentation? = null
        val session = WebPresentationSession(context, "web", "phrase", project, this) { _, next -> current = next }
        val data = JSONObject("""{"appearance":{"background":"background.png"}}""")
        assertTrue(runCatching { session.apply(data, false, { true }) { true } }.isFailure)
        val outside = JSONObject("""{"appearance":{"background":"../background.png"}}""")
        assertTrue(runCatching { session.apply(outside, true, { true }) { true } }.isFailure)
        assertNull(current)
        assertTrue(session.apply(data, true, { true }) { true })
        val asset = requireNotNull(current?.appearance?.background)
        assertTrue(session.setControls(JSONObject("""{"fadeControlsWhenIdle":true}"""), { true }) { true })
        assertEquals(asset, current?.appearance?.background)
        assertTrue(AppearanceAssets(context).file(asset)?.isFile == true)
        session.clear()
        assertNull(current)
    }
}
