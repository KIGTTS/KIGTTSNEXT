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
