package com.kigtts.next.sharing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.identity.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.kigtts.next.CardPrintCoordinatorTest
import kotlinx.coroutines.cancel
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaceProfileWebTest {
    @Test fun titleOnlyTextTilesUseCenteredLargerTypeWithoutAnEmptyBodyOrChangingHeadings() {
        val tiles = listOf(ProfileTile(id = "short", title = "你好"),
            ProfileTile(id = "tall", title = "很高兴认识你", text = " \n", rows = 2),
            ProfileTile(id = "heading", kind = ProfileTileKind.TITLE, title = "标题"))
        val json = profile().put("tiles", profileTileJson(tiles))
        val html = faceWebTiles(PublicIdentityProfile.from(json), "./", emptyList()) { "" }
        assertEquals(2, Regex("tile-text-title-only").findAll(html).count())
        assertTrue(html.contains("--tile-title-lines:4"))
        assertTrue(html.contains("<strong>你好</strong></section>"))
        assertTrue(html.contains("tile-title\""))
        assertTrue(faceTileStyles.contains("text-align:center"))
        assertTrue(faceTileStyles.contains("font-size:22px"))
    }

    @Test fun everyContactPlatformHasABundledVectorThatAlsoRendersInTheSharedWebPage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val icons = FaceWebIcons(app)
        SocialPlatform.entries.forEach { platform ->
            val svg = icons.icon(ContactLink(platform.displayName, "account"))
            assertTrue(platform.displayName, svg.contains("<svg ") && svg.contains("<path "))
        }
    }

    private fun profile() = JSONObject().put("nickname", "你好，KIGTTS").put("bio", "很高兴认识你。\n这里是我的联系方式。")
        .put("contacts", JSONArray().put(JSONObject().put("label", "QQ").put("value", "12345678"))
            .put(JSONObject().put("label", "GitHub").put("value", "https://github.com/KIGTTS"))
            .put(JSONObject().put("label", "微信").put("value", "my-account").put("qrContent", "https://example.com/contact")))
        .put("tags", JSONArray(listOf("Kigurumi", "开发", "摄影")))
        .put("tiles", profileTileJson(listOf(ProfileTile(id = "heading", kind = ProfileTileKind.TITLE, title = "很高兴认识你"),
            ProfileTile(id = "text", title = "日常", text = "记录生活，也做一些有趣的小工具。", rows = 2),
            ProfileTile(id = "link", kind = ProfileTileKind.LINK, title = "项目主页", text = "https://github.com/KIGTTS", rows = 1),
            ProfileTile(id = "social", kind = ProfileTileKind.CONTACT, contact = "12345678", title = "QQ", rows = 1))))
    @Test fun webUsesNativeThemeRolesAndTheSameCompactTitleAndTilePositions() {
        val theme = com.kigtts.next.media.WebTheme(true, mapOf("primary" to "#ffcc44", "surface" to "#111122"), 18)
        val page = faceGallery(profile(), emptyList(), "/s/test/", theme = theme)
        assertTrue(page.contains("--md-sys-color-primary:#ffcc44")); assertTrue(page.contains("--kigtts-corner:18px"))
        assertTrue(page.contains("grid-template-rows:36px")); assertTrue(page.contains("Kigurumi"))
        assertTrue(page.contains("data-contact=\"contact-0\"")); assertTrue(page.contains("kigtts-theme-change"))
        assertTrue(page.contains("fetch('/s/test/theme'"))
        assertTrue(faceWebTheme(theme).colors.containsKey("surface-container-high"))
    }
    @Test fun oneCellContactAndLinkTilesOnlyRenderIconsWithAccessibleActions() {
        val json = profile().put("tiles", profileTileJson(listOf(
            ProfileTile(id = "social", kind = ProfileTileKind.CONTACT, contact = "12345678", title = "QQ 小格", columns = 1),
            ProfileTile(id = "link", kind = ProfileTileKind.LINK, title = "项目 \"A\"", text = "https://example.com", columns = 1),
            ProfileTile(id = "wide", kind = ProfileTileKind.LINK, title = "宽磁贴", text = "https://example.org"))))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val html = faceWebTiles(PublicIdentityProfile.from(json), "/s/test/", emptyList(), FaceWebIcons(app)::icon)
        assertEquals(2, Regex("tile-icon-only").findAll(html).count())
        assertEquals(3, Regex("<svg ").findAll(html).count())
        assertTrue(html.contains("aria-label=\"项目 &quot;A&quot;\""))
        assertFalse(html.contains("<strong>QQ 小格</strong>")); assertFalse(html.contains("<strong>项目 &quot;A&quot;</strong>"))
        assertFalse(html.contains("<small>12345678</small>"))
        assertTrue(html.contains("data-contact=\"contact-0\"")); assertTrue(html.contains("href=\"https://example.com\""))
        assertTrue(html.contains("<strong>宽磁贴</strong>")); assertTrue(html.contains("<span>https://example.org</span>"))
    }
    @Test fun personalShareHasOneProfileAndContactQrWithoutPhotoSelectionControls() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val banner = FaceAsset("preview.png", "profile", "polaroid", "名片.png", "image/png", 1, "", "BANNER")
        val page = faceGallery(profile(), listOf(banner), "./", FaceWebIcons(app)::icon)
        assertTrue(page.contains("<title>个人名片</title>")); assertTrue(page.contains("保存名片图片"))
        assertFalse(page.contains("<footer>")); assertFalse(page.contains("data-photo="))
        assertTrue(page.contains("class=\"contact-qr\"")); assertTrue(page.contains("href=\"https://github.com/KIGTTS\""))
        assertEquals(3, Regex("class=\"contact-link\"").findAll(page).count())
        val directory = File("build/previews/face-web").apply { mkdirs() }
        File(directory, "profile.html").writeText(page)
        File(directory, "file").mkdirs()
        val fixture = CardPrintCoordinatorTest.Fixture()
        try {
            val repository = IdentityRepository(fixture.app, fixture.cards)
            val person = PublicIdentityProfile.from(profile())
            val identity = repository.create().copy(nickname = person.name, bio = person.bio, contacts = person.contacts)
            exportIdentityCard(identity, fixture.stickers, directory).copyTo(File(directory, "file/preview.png"), overwrite = true)
        } finally { fixture.scope.cancel() }
        val photo = banner.copy(id = "photo.png", format = "SQUARE")
        File(directory, "photos.html").writeText(faceGallery(profile(), listOf(photo, photo.copy(id = "second.png")), "./", FaceWebIcons(app)::icon))
        File(directory, "file/photo.png").writeBytes(File(directory, "file/preview.png").readBytes())
        File(directory, "file/second.png").writeBytes(File(directory, "file/preview.png").readBytes())
        File(directory, "theme").writeText(faceWebTheme(com.kigtts.next.media.WebTheme()).json().toString())
        File(directory, "favicon.ico").writeBytes(byteArrayOf())
    }
    @Test fun importedProfilesCannotInjectMarkupOrExecutableLinksAndMalformedContactsAreIgnored() {
        val json = JSONObject().put("nickname", "<img src=x onerror=alert(1)>")
            .put("contacts", JSONArray().put("broken").put(JSONObject().put("label", "<script>"+"alert(1)</script>")
                .put("value", "javascript:alert(1)")).put(JSONObject().put("label", "邮箱").put("value", "hi@example.com")))
        val page = faceGallery(json, emptyList(), "/s/test/")
        assertFalse(page.contains("<img src=x")); assertFalse(page.contains("href=\"javascript:"))
        assertTrue(page.contains("&lt;script&gt;")); assertTrue(page.contains("href=\"mailto:hi@example.com\""))
        assertEquals(2, PublicIdentityProfile.from(json).contacts.size)
        assertNull(faceContactUrl(ContactLink("链接", "data:text/html,<script>")))
        assertNull(faceContactUrl(ContactLink("链接", "https://user@example.com")))
    }
    @Test fun aLongQrDoesNotBreakTheEntireProfileOrItsOtherContacts() {
        val json = JSONObject().put("nickname", "长内容").put("contacts", JSONArray().put(JSONObject().put("label", "备注").put("value", "长".repeat(2048))))
        val page = faceGallery(json, emptyList(), "/s/test/")
        assertTrue(page.contains("内容较长，请复制下方文字。")); assertTrue(page.contains("data-copy=\"contact-0\""))
    }
}
