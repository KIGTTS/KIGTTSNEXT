package com.kigtts.next.identity

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.ExternalFileType
import com.kigtts.next.state.ExternalFileTypes
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.AppearanceAssets
import com.kigtts.next.theme.ThemeSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], shadows = [ProfileRegionTestShadow::class])
class OfflineProfileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun archiveRestoresKnownTypesAndIgnoresMislabelledMediaAndArbitraryWebArchives() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        val cover = model.identities.create()
        val photo = File(temporary.root, "test-photo.png")
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(0xff008888.toInt())
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val asset = model.stickers.importResource(photo)
        val person = cover.copy(nickname = "小怪 <测试>", bio = "离线名片",
            contacts = listOf(ContactLink("站点", "https://example.org/a")),
            tiles = listOf(ProfileTile(kind = ProfileTileKind.IMAGE, title = "可点开的图", image = asset.id, rows = 2),
                ProfileTile(kind = ProfileTileKind.QR, text = "中文二维码", rows = 2),
                ProfileTile(kind = ProfileTileKind.LINK, title = "外部链接", text = "https://example.org/a")))
        val file = ProfileArchive.write(person, model.stickers, temporary.newFolder("archive"))
        file.copyTo(File("build/previews/profile-archive-fixture.mhtml").apply { parentFile.mkdirs() }, overwrite = true)
        assertEquals(ExternalFileType.PROFILE_ARCHIVE, ExternalFileTypes.inspect(file, "received.mhtml"))
        assertNull(OfflineProfileData.parse("{\"profile\":" + "[".repeat(32) + "]".repeat(32) + "}"))
        assertNull(OfflineProfileData.snapshot(JSONObject().put("nickname", 42)))
        val favorites = ContactFavorites.get(app)
        val added = ProfileArchive.restore(file, temporary.newFolder("imported"), favorites)
        try {
            val restored = PublicIdentityProfile.from(JSONObject(added.profile))
            assertEquals(person.nickname, restored.name)
            assertEquals(person.contacts, restored.contacts)
            assertEquals("中文二维码", restored.tiles[1].text)
            assertTrue(added.cover?.isFile == true)
            assertTrue(requireNotNull(favorites.image(restored.tiles.first().image)).isFile)
            val partial = File(temporary.root, "partial.mhtml").apply {
                writeText(file.readText().replace("Content-Type: image/jpeg\r\nContent-Location: https://kigtts.invalid/tile-0.jpg",
                    "Content-Type: image/svg+xml\r\nContent-Location: https://kigtts.invalid/tile-0.jpg"))
            }
            val partialImport = ProfileArchive.restore(partial, temporary.newFolder("partial"), favorites)
            assertEquals(added.id, partialImport.id)
            assertTrue(PublicIdentityProfile.from(JSONObject(partialImport.profile)).tiles.first().image.isEmpty())
            assertEquals(1, favorites.items.value.count { it.id == added.id })
            val outside = File(temporary.root, "outside.mhtml").apply { writeText("MIME-Version: 1.0\r\n\r\n<script>throw 1;</script>") }
            assertEquals(ExternalFileType.UNKNOWN, ExternalFileTypes.inspect(outside, "outside.mhtml"))
        } finally { favorites.remove(added.id) }
    }

    @Test fun longImageDeduplicatesTargetsAndLeavesPhotoTilePreviewsOut() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        val identity = model.identities.create().copy(nickname = "测试名片",
            contacts = listOf(ContactLink("官网", "https://EXAMPLE.org/one")),
            tiles = listOf(ProfileTile(kind = ProfileTileKind.LINK, title = "同一链接", text = "https://example.org/one"),
                ProfileTile(kind = ProfileTileKind.LINK, title = "另一个链接", text = "https://example.org/two"),
                ProfileTile(kind = ProfileTileKind.LINK, text = "javascript:alert(1)")))
        val links = ProfileLongImage.links(identity)
        assertEquals(2, links.size)
        assertEquals(listOf("官网", "同一链接"), links.first().labels)
        val photo = ProfileTile(kind = ProfileTileKind.IMAGE, title = "图片磁贴")
        val withPlaceholder = identity.copy(tiles = identity.tiles + photo)
        val normal = ProfileLongImage.write(withPlaceholder, model.stickers, temporary.newFolder("long"))
        val withPhoto = ProfileLongImage.write(withPlaceholder.copy(tiles = identity.tiles +
            photo.copy(image = "different-private-image")), model.stickers, temporary.newFolder("long-photos"))
        assertArrayEquals(normal.readBytes(), withPhoto.readBytes())
        val image = BitmapFactory.decodeFile(normal.path)
        try {
            assertEquals(840, image.width)
            assertTrue(image.height > 800)
        } finally { image.recycle() }
    }

    @Test fun longImageRestoresFromColorStripAfterRecompressionAndKeepsPhotosOnlyWithEmbeddedSource() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        val photo = File(temporary.root, "tile.png")
        Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(0xff2284dd.toInt())
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val design = model.identities.create()
        val person = design.copy(nickname = "可以还原的长图", gradient = true, gradientColor = 0xff7346ab,
            contacts = listOf(ContactLink("首页", "https://example.org")), tiles = listOf(
                ProfileTile(kind = ProfileTileKind.IMAGE, title = "源图片", image = model.stickers.importResource(photo).id),
                ProfileTile(kind = ProfileTileKind.TEXT, title = "介绍", text = "离线资料与中文内容")))
        val naked = ProfileLongImage.write(person, model.stickers, temporary.newFolder("plain"))
        assertFalse(ProfilePngPayload.extract(naked, File(temporary.root, "no-data.mhtml")))
        val jpeg = File(temporary.root, "compressed.jpg")
        val bitmap = BitmapFactory.decodeFile(naked.path)
        // Both sides and the bottom are occupied by the same four-level pattern.
        fun lastCell(x: Int) = bitmap.getPixel(x, bitmap.height - 1)
        assertEquals(lastCell(36 + (192 - 9) * 4), lastCell(0))
        assertEquals(lastCell(36 + 8 * 4), lastCell(bitmap.width - 1))
        assertTrue((2 until bitmap.width step 4).map(::lastCell).toSet().size == 4)
        val resized = Bitmap.createScaledBitmap(bitmap, 630, (bitmap.height * .75f).toInt(), true)
        jpeg.outputStream().use { assertTrue(resized.compress(Bitmap.CompressFormat.JPEG, 84, it)) }
        resized.recycle(); bitmap.recycle()
        val favorites = ContactFavorites.get(app)
        assertNull(ProfileLongCardRestore.restore(photo, temporary.newFolder("ordinary"), favorites))
        val imported = requireNotNull(ProfileLongCardRestore.restore(jpeg, temporary.newFolder("cropped"), favorites))
        try {
            val cropped = PublicIdentityProfile.from(JSONObject(imported.profile))
            assertEquals(person.nickname, cropped.name)
            assertEquals("离线资料与中文内容", cropped.tiles[1].text)
            assertTrue(imported.cover?.isFile == true)
            assertTrue(cropped.tiles.first().image.isEmpty())
            val complete = ProfileLongImage.write(person, model.stickers, temporary.newFolder("with-source"), embedSource = true)
            assertTrue(ProfilePngPayload.extract(complete, File(temporary.root, "data.mhtml")))
            val restored = requireNotNull(ProfileLongCardRestore.restore(complete, temporary.newFolder("restored"), favorites))
            assertEquals(imported.id, restored.id)
            assertTrue(favorites.image(PublicIdentityProfile.from(JSONObject(restored.profile)).tiles.first().image)?.isFile == true)
        } finally { favorites.remove(imported.id) }
    }

    @Test fun grayCodeCanStillReadBothOlderMargins() {
        val payload = """{"nickname":"之前分享的名片"}"""
        val code = ProfileGrayCode.encode(payload)
        for (margin in listOf(32, 76)) {
            val bitmap = Bitmap.createBitmap(840, 500, Bitmap.Config.ARGB_8888)
            try {
                code.draw(Canvas(bitmap), bitmap.height - margin, 0xff5346bc.toInt())
                assertEquals(payload, ProfileGrayCode.decode(bitmap.width, bitmap.height) { band ->
                    Bitmap.createBitmap(bitmap, 0, bitmap.height - band, bitmap.width, band)
                })
            } finally { bitmap.recycle() }
        }
    }

    @Test fun longImageUsesActiveVariableFontForBothCoverAndTiles() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        val font = File(temporary.root, "export-font.ttf")
        requireNotNull(javaClass.getResourceAsStream("/fixtures/variable-font/fixture.ttf")).use { input ->
            font.outputStream().use(input::copyTo)
        }
        val key = AppearanceAssets(app).importFont(Uri.fromFile(font))
        val person = model.identities.create().copy(nickname = "KIGTTS", bio = "Ag KIGTTS",
            tiles = listOf(ProfileTile(kind = ProfileTileKind.TEXT, title = "Ag KIGTTS", text = "KIGTTS")))
        fun export(weight: Int) = ProfileLongImage.write(person, model.stickers, temporary.newFolder("weight-$weight"),
            appearance = ThemeSettings(font = key, fontWeight = weight))
        val light = BitmapFactory.decodeFile(export(200).path)
        val heavyFile = export(700)
        val heavy = BitmapFactory.decodeFile(heavyFile.path)
        try {
            fun pixels(bitmap: Bitmap, top: Int, rows: Int) = IntArray(bitmap.width * rows).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, top, bitmap.width, rows)
            }
            // Export geometry: 16:9 cover at y=32, followed by the first tile at y=488.
            assertFalse("Cover did not use the selected weight", pixels(light, 300, 145).contentEquals(pixels(heavy, 300, 145)))
            assertFalse("Tile did not use the selected weight", pixels(light, 488, 150).contentEquals(pixels(heavy, 488, 150)))
            heavyFile.copyTo(File(File("build/previews").apply { mkdirs() }, "profile-long-wordmark.png"), overwrite = true)
        } finally { light.recycle(); heavy.recycle() }
    }
}

/** The standard Robolectric region shadow returns a solid empty bitmap. */
@Implements(BitmapRegionDecoder::class)
class ProfileRegionTestShadow {
    private lateinit var image: Bitmap

    @Implementation fun getWidth(): Int = image.width
    @Implementation fun getHeight(): Int = image.height
    @Implementation fun recycle() { image.recycle() }
    @Implementation fun decodeRegion(rect: Rect, opts: BitmapFactory.Options): Bitmap {
        val crop = Bitmap.createBitmap(image, rect.left, rect.top, rect.width(), rect.height())
        val divisor = opts.inSampleSize.coerceAtLeast(1)
        return if (divisor == 1) crop else Bitmap.createScaledBitmap(crop,
            (crop.width / divisor).coerceAtLeast(1), (crop.height / divisor).coerceAtLeast(1), false).also { crop.recycle() }
    }
    companion object {
        @JvmStatic @Implementation
        fun newInstance(path: String, @Suppress("UNUSED_PARAMETER") shareable: Boolean): BitmapRegionDecoder? {
            val source = BitmapFactory.decodeFile(path) ?: return null
            val decoder = ReflectionHelpers.callConstructor(BitmapRegionDecoder::class.java,
                ReflectionHelpers.ClassParameter.from(Long::class.javaPrimitiveType!!, 0L))
            Shadow.extract<ProfileRegionTestShadow>(decoder).image = source
            return decoder
        }
    }
}
