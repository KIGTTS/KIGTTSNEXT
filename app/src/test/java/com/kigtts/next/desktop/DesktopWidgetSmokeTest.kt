package com.kigtts.next.desktop

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.MainActivity
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.identity.ContactLink
import com.kigtts.next.identity.IdentityCard
import com.kigtts.next.identity.IdentityRepository
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.identity.ProfileTileKind
import com.kigtts.next.manager.HostPluginRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopWidgetSmokeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun eachShortcutUsesTheMainActivityAndItsOwnAction() {
        val routes = DesktopEntry.entries.map { it.intent(context) }
        assertTrue(routes.all { it.component?.className == MainActivity::class.java.name })
        assertEquals(DesktopEntry.entries.size, routes.map { it.action }.distinct().size)
        assertEquals(DesktopEntry.entries, routes.map(DesktopEntry::from))
    }

    @Test fun profileWidgetPrefersARealQrTileAndKeepsAnImportedContactCode() {
        val qr = ProfileTile(kind = ProfileTileKind.QR, title = "现场", text = "https://example.org/")
        val card = IdentityCard(designId = "design", contacts = listOf(ContactLink("QQ", "account", "二维码")), tiles = listOf(qr))
        assertEquals(qr, desktopProfileQr(card))
        assertEquals("二维码", desktopProfileQr(card.copy(tiles = emptyList()))?.text)
    }

    @Test fun quickWidgetProducesClickableGlanceContentInWideAndTallSizes() = runBlocking {
        withTimeout(20_000) {
            for (size in listOf(DpSize(290.dp, 54.dp), DpSize(202.dp, 154.dp))) {
                val content = QuickEntryWidget.compose(context, size = size)
                val view = content.apply(context, FrameLayout(context))
                assertNotNull(view.findContent("KIGTTS"))
                for (entry in DesktopEntry.quick) {
                    val target = requireNotNull(view.findContent(context.uiCopy(entry.label))) {
                        "Missing label for ${entry.id} at $size"
                    }
                    val clickable = generateSequence(target) { it.parent as? View }.any { it.hasOnClickListeners() }
                    assertTrue("Missing action for ${entry.id} at $size", clickable)
                }
            }
        }
    }

    @Test fun personalCardWidgetShowsTheSavedProfileAndQrAndShareActions() = runBlocking {
        withTimeout(20_000) {
            val runtime = HostPluginRuntime.get(context.applicationContext as Application)
            val repo = IdentityRepository(context, runtime.cards)
            val saved = repo.create().copy(nickname = "Preview card", bio = "Widget smoke test", tiles =
                listOf(ProfileTile(kind = ProfileTileKind.QR, text = "https://example.org/")))
            repo.save(saved)
            val card = ProfileCardWidget.compose(context, size = DpSize(280.dp, 180.dp))
                .apply(context, FrameLayout(context))
            assertNotNull(card.findContent(saved.nickname))
            assertNotNull(card.findContent(saved.bio))
            assertNotNull(card.findContent(context.uiCopy(DesktopEntry.PROFILE_QR.label)))
            assertNotNull(card.findContent(context.uiCopy(DesktopEntry.PROFILE_SHARE.label)))
        }
    }

    private fun View.findContent(label: String): View? {
        if (contentDescription?.toString() == label || this is TextView && text?.toString() == label) return this
        return (this as? ViewGroup)?.let { group ->
            (0 until group.childCount).firstNotNullOfOrNull { group.getChildAt(it).findContent(label) }
        }
    }
}
