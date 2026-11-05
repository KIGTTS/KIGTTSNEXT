package com.kigtts.next.identity

import com.kigtts.next.CardPrintCoordinatorTest
import com.kigtts.next.media.WebTheme
import com.kigtts.next.media.WebThemeState
import com.kigtts.next.theme.*
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileWebAppearanceTest {
    @Test fun publicWebProfileCarriesActualFontWeightHeightIconsAndNativePlacementFixture() {
        val f = CardPrintCoordinatorTest.Fixture()
        val before = WebThemeState.current
        try {
            val bytes = javaClass.getResourceAsStream("/fixtures/variable-font/fixture.ttf")!!.use { it.readBytes() }
            val key = AppearanceAssets.digest(bytes) + ".ttf"
            File(f.app.filesDir, "appearance-assets/$key").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            val background = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                .apply { eraseColor(android.graphics.Color.rgb(80, 130, 100)) }
            val backgroundKey = try { AppearanceAssets(f.app).saveBackground(background) } finally { background.recycle() }
            ThemeSettingsStore(f.app).save(ThemeSettings(font = key, fontWeight = 650, cornerLevel = CornerLevel.MEDIUM,
                background = backgroundKey, backgroundDim = .35f))
            WebThemeState.current = WebTheme(dark = false, colors = mapOf("primary" to "#13676b", "surface" to "#f4fafb"))
            val identity = IdentityRepository(f.app, f.cards).create().copy(nickname = "KIGTTS", bio = "一起记录日常。",
                contacts = listOf(ContactLink("QQ", "12345678")), tags = listOf("摄影", "开发"), tiles = listOf(
                    ProfileTile(id = "title", kind = ProfileTileKind.TITLE, title = "很高兴认识你"),
                    ProfileTile(id = "hello", title = "你好"),
                    ProfileTile(id = "social", kind = ProfileTileKind.CONTACT, columns = 1, contact = "12345678"),
                    ProfileTile(id = "link", kind = ProfileTileKind.LINK, columns = 1, text = "https://lhtstudio.com", title = "网站"),
                    ProfileTile(id = "tall", title = "日常", text = "文字和图片都可以放在这里。", rows = 2),
                    ProfileTile(id = "next", kind = ProfileTileKind.TITLE, title = "更多内容"),
                    ProfileTile(id = "wide", title = "Hello", columns = 4)))
            val directory = File("build/previews/cloud-profile").apply { mkdirs() }
            val pdf = File(directory, "profile.pdf").apply { writeText("fixture") }
            val files = profileSharingFiles(identity, f.stickers, directory, pdf)
            val json = JSONObject(files.single { it.name == "profile.json" }.readText())
            val appearance = json.getJSONObject("appearance")
            assertArrayEquals(bytes, files.single { it.name == "profile-font.ttf" }.readBytes())
            assertEquals(650.0, appearance.getJSONObject("weights").getDouble("400"), 0.0)
            assertEquals(950.0, appearance.getJSONObject("weights").getDouble("700"), 0.0)
            assertTrue(appearance.getDouble("fontScale") in .65..1.6)
            assertEquals(22.0, appearance.getDouble("tileCorner"), 0.0)
            assertTrue(appearance.getJSONArray("contactIcons").getString(0).contains("<path"))
            assertEquals(identity.tiles.size, appearance.getJSONArray("tileIcons").length())
            assertEquals("#13676b", appearance.getJSONObject("theme").getJSONObject("colors").getString("primary"))
            assertEquals(.48, appearance.getDouble("backgroundDim"), .001)
            assertTrue(files.single { it.name == appearance.getString("background") }.length() > 0)
            val lan = File("build/previews/lan-profile").apply { mkdirs() }
            val localAppearance = com.kigtts.next.sharing.FaceWebAppearance.prepare(f.app, File(lan, "appearance"))
            val banner = files.first { it.extension == "png" && it.name != "share-background.png" }
            File(lan, "file").mkdirs()
            banner.copyTo(File(lan, "file/banner.png"), overwrite = true)
            val asset = com.kigtts.next.sharing.FaceAsset("banner.png", "profile", "polaroid", "名片.png", "image/png", banner.length(), "", "BANNER")
            File(lan, "index.html").writeText(com.kigtts.next.sharing.faceGallery(json, listOf(asset), "./",
                com.kigtts.next.sharing.FaceWebIcons(f.app)::icon, appearance = localAppearance.json))
            com.kigtts.next.sharing.FacePhotoSession(File(lan, "session"), { json }, received = { _, _ -> }).use { session ->
                session.webAppearance = localAppearance
                val base = "http://127.0.0.1:${session.server.port}"
                val connection = java.net.URL(base + session.path + "appearance/profile-font.ttf").openConnection() as java.net.HttpURLConnection
                assertArrayEquals(bytes, connection.inputStream.use { it.readBytes() })
                assertTrue(connection.getHeaderField("Content-Security-Policy").contains("font-src 'self'"))
                fun status(path: String) = (java.net.URL(base + path).openConnection() as java.net.HttpURLConnection).run {
                    try { responseCode } finally { disconnect() }
                }
                assertEquals(404, status("/s/${session.peerToken}/appearance/profile-font.ttf"))
                assertEquals(404, status(session.path + "appearance/private-file.ttf"))
                val old = session.path
                session.endBrowserShare()
                assertEquals(404, status(old + "appearance/profile-font.ttf"))
            }
            ThemeSettingsStore(f.app).save(ThemeSettings(background = backgroundKey, secondaryPageBackground = false))
            val plain = com.kigtts.next.sharing.shareWebAppearance(f.app, directory, mutableListOf())
            assertFalse(plain.has("background"))
            assertTrue(com.kigtts.next.sharing.webAppearanceAsset("share-background.png"))
            assertFalse(com.kigtts.next.sharing.webAppearanceAsset("photo.png"))
            val placements = packProfileTiles(identity.tiles).map { JSONObject().put("id", it.tile.id)
                .put("column", it.column).put("row", it.row).put("columns", it.tile.columns).put("rows", it.tile.rows) }
            File(directory, "native-placements.json").writeText(org.json.JSONArray(placements).toString())
        } finally { WebThemeState.current = before; f.scope.cancel() }
    }
}
