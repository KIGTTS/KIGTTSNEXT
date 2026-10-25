package com.kigtts.next

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.links.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class ExternalLinksTest {
    @Test fun trustChecksRealHttpsHostAndPort() {
        listOf("https://lhtstudio.com/a", "https://events.lhtstudio.com/a", "https://LHTSTUDIO.COM:443/a").forEach { assertTrue(it, TrustedWebsite.contains(it)) }
        listOf("http://lhtstudio.com", "https://lhtstudio.com.evil.org", "https://evillhtstudio.com", "https://lhtstudio.com@evil.org", "https://lhtstudio.com:444", "file://lhtstudio.com/a").forEach { assertFalse(it, TrustedWebsite.contains(it)) }
    }
    @Test fun parsesBrowserAndQuickTextWithoutEncodingConfusion() {
        val url = "https://events.lhtstudio.com/raffle?a=1&b=2"
        val uri = Uri.parse("kigtts://open").buildUpon().appendQueryParameter("url", url).appendQueryParameter("mode", "custom-tab")
            .appendQueryParameter("add", "true").appendQueryParameter("title", "活动 & 问候").build()
        val link = requireNotNull(ExternalLinkParser.parse(uri))
        assertEquals(url, link.url); assertTrue(link.trusted); assertTrue(link.add)
        assertEquals(WebOpenMode.CUSTOM_TAB, link.mode); assertEquals("活动 & 问候", link.title)
        assertEquals("你好", ExternalLinkParser.parse(Uri.parse("https://lhtstudio.com/kigtts/quick-text?text=你好"))?.text)
        assertNull(ExternalLinkParser.parse(Uri.parse("https://evil.org/kigtts/open?url=https://lhtstudio.com")))
        assertNull(ExternalLinkParser.parse(Uri.parse("kigtts://source/add?url=https://lhtstudio.com")))
    }
    @Test fun rejectsAmbiguousOrExecutableRoutes() {
        listOf("kigtts://open?url=javascript%3Aalert(1)", "kigtts://open?url=https://good.org&url=https://evil.org",
            "kigtts://open?text=hello&mode=custom-tab", "kigtts://open?url=https://good.org&add=maybe").forEach {
            assertTrue(it, runCatching { ExternalLinkParser.parse(Uri.parse(it)) }.isFailure)
        }
    }
    @Test fun nfcRecordUsesSameValidatedRoute() {
        val uri = Uri.parse("kigtts://quick-text?text=问候&add=true")
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED).putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES,
            arrayOf(NdefMessage(arrayOf(NdefRecord.createUri(uri)))))
        assertEquals("问候", ExternalLinkParser.from(intent)?.text)
        assertTrue(ExternalLinkParser.from(intent)?.add == true)
    }
    @Test fun queueRetainsNextLinkAndSavingIsIdempotent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        val first = ExternalLink(text = "NFC保存测试", title = "测试", add = true)
        val second = ExternalLink(url = "https://lhtstudio.com", mode = WebOpenMode.CUSTOM_TAB)
        model.externalLinks.offer(first); model.externalLinks.offer(second)
        assertEquals(first, model.externalLinks.pending.value)
        val count = model.phrases.value.size
        model.externalLinks.perform(first); model.externalLinks.perform(first)
        assertEquals(count + 1, model.phrases.value.size)
        model.externalLinks.dismiss(); assertEquals(second, model.externalLinks.pending.value)
        model.externalLinks.dismiss(); assertNull(model.externalLinks.pending.value)
    }
}
