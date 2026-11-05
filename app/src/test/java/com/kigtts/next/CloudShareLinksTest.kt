package com.kigtts.next

import android.net.Uri
import com.kigtts.next.sharing.CloudShareLinks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [26, 35])
class CloudShareLinksTest {
    private val link = "https://app.kigtts.lhtstudio.com/kigtts/share/${"a".repeat(22)}#${"b".repeat(43)}"
    @Test fun externalAndInternalLinksUseOneExactBoundedInvitation() {
        assertEquals(link, CloudShareLinks.parse(link))
        assertEquals(link, CloudShareLinks.parse("kigtts://share?url=${Uri.encode(link)}"))
        for (bad in listOf(link.replace("app.kigtts.lhtstudio.com", "app.kigtts.lhtstudio.com.evil.com"),
            link.replace("https://", "http://"), link.replace("https://", "https://name@"), link.substringBefore('#'),
            link.replace("/share/", "/../share/"), link.replace("#", "?extra=x#"), link + "x",
            "kigtts://share?url=${Uri.encode(link)}&url=${Uri.encode(link)}")) assertNull(bad, CloudShareLinks.parse(bad))
    }
}
