package com.kigtts.next.identity

import com.kigtts.next.CardPrintCoordinatorTest
import com.kigtts.next.sharing.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileTilesTest {
    @Test fun socialAccountsSurviveRestartAndPublicSharingWithoutDependingOnProfileContacts() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val repo = IdentityRepository(f.app, f.cards)
            val tiles = listOf(ProfileTile(id = "main", kind = ProfileTileKind.CONTACT, social = ContactLink("小红书", "https://xhslink.com/a")),
                ProfileTile(id = "other", kind = ProfileTileKind.CONTACT, social = ContactLink("小红书", "another-account", "custom-qr")))
            repo.save(repo.create().copy(tiles = tiles))
            val restored = IdentityRepository(f.app, f.cards).current!!
            assertTrue(restored.contacts.isEmpty())
            assertEquals(tiles, restored.tiles)
            val public = PublicIdentityProfile.from(restored.publicJson())
            assertEquals(tiles, public.tiles)
            assertEquals(tiles.map { it.social }, public.tiles.map { it.resolveContact(emptyList()) })
            val legacy = ProfileTile.from(JSONObject().put("kind", "CONTACT").put("contact", "123"))
            assertEquals(ContactLink("QQ", "123"), legacy.resolveContact(listOf(ContactLink("QQ", "123"))))
        } finally { f.scope.cancel() }
    }

    @Test fun importedSocialTileFieldsAreBoundedAndEmptyAccountsAreIgnored() {
        val json = JSONObject().put("kind", "CONTACT").put("social", JSONObject().put("label", "a".repeat(100))
            .put("value", "b".repeat(5000)).put("qrContent", "c".repeat(5000)))
        val social = ProfileTile.from(json).social!!
        assertEquals(24, social.label.length); assertEquals(2048, social.value.length); assertEquals(2048, social.qrContent!!.length)
        json.getJSONObject("social").put("value", "  ")
        assertNull(ProfileTile.from(json).social)
    }

    @Test fun gifRemainsAnimatedThroughPreparingAndReceivingAPersonalCard() = runBlocking {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val repo = IdentityRepository(f.app, f.cards)
            val image = java.util.UUID.randomUUID().toString()
            val file = File(f.app.filesDir, "profile-images/$image").apply { parentFile!!.mkdirs() }
            val bytes = javaClass.classLoader!!.getResourceAsStream("fixtures/profile/animated.gif")!!.use { it.readBytes() }
            file.writeBytes(bytes)
            val tile = ProfileTile(kind = ProfileTileKind.IMAGE, image = image)
            val identity = repo.create().copy(nickname = "GIF 名片", tiles = listOf(tile))
            repo.save(identity)
            val receiver = FacePhotoRepository(f.cards, repo)
            val assets = receiver.prepare(listOf(identity.designId), false, true, File(f.app.cacheDir, "gif-send"))
            val gif = assets.single { it.format == "PROFILE" }
            assertEquals("image/gif", gif.mime); assertEquals("profile", FaceAsset.parse(gif.json()).variant)
            assertArrayEquals(bytes, gif.file!!.readBytes())
            receiver.receive(FaceOffer(faceToken(), faceToken(), identity.publicJson(), assets), assets)
            val saved = ContactFavorites.get(f.app).items.value.single()
            val receivedImage = profileTiles(JSONObject(saved.profile)).single().image
            assertArrayEquals(bytes, ContactFavorites.get(f.app).image(receivedImage)!!.readBytes())
            assertTrue(f.cards.cardsInAlbum("face-received").isEmpty())
        } finally { f.scope.cancel() }
    }
    @Test fun resizingAcrossAllSupportedSizesCannotOverlapOrPlaceSectionContentAboveItsTitle() {
        for (columns in 1..4) for (rows in 1..3) {
            val tiles = listOf(ProfileTile(id = "first", columns = 3, rows = 2), ProfileTile(id = "title", kind = ProfileTileKind.TITLE),
                ProfileTile(id = "resized", columns = columns, rows = rows), ProfileTile(id = "last", columns = 2, rows = 2))
            val packed = packProfileTiles(tiles)
            val occupied = mutableSetOf<Pair<Int, Int>>()
            packed.forEach { p ->
                assertTrue(p.column + p.tile.columns <= 4)
                for (x in p.column until p.column + p.tile.columns) for (y in p.row until p.row + p.tile.rows) assertTrue(occupied.add(x to y))
            }
            val heading = packed[1]
            assertEquals(4, heading.tile.columns); assertEquals(1, heading.tile.rows)
            assertTrue(packed[2].row > heading.row); assertTrue(packed[3].row > heading.row)
        }
    }
    @Test fun allTileKindsAndTagsSurviveRestartAndPublicSharing() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val repo = IdentityRepository(f.app, f.cards)
            val tiles = ProfileTileKind.entries.map { ProfileTile(kind = it, title = it.name, text = "内容", image = "image.gif") }
            repo.save(repo.create().copy(tags = listOf("Kigurumi", "开发"), tiles = tiles))
            val restored = IdentityRepository(f.app, f.cards).current!!
            assertEquals(tiles, restored.tiles); assertEquals(listOf("Kigurumi", "开发"), restored.tags)
            val public = PublicIdentityProfile.from(restored.publicJson())
            assertEquals(tiles, public.tiles); assertEquals(restored.tags, public.tags)
        } finally { f.scope.cancel() }
    }
    @Test fun receivedCardIsAnIndependentFavoriteWithGifBytesAndStableDeduplication() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val favorites = ContactFavorites.get(f.app)
            val tile = ProfileTile(kind = ProfileTileKind.IMAGE, image = "original")
            val profile = JSONObject().put("identityId", "same-person").put("id", "session-one").put("nickname", "朋友")
                .put("tags", JSONArray(listOf("摄影"))).put("tiles", profileTileJson(listOf(tile)))
            val image = File(f.app.cacheDir, "animated.gif").apply { writeBytes(byteArrayOf(71, 73, 70, 56, 57, 97, 1, 0, 1, 0)) }
            val first = favorites.save(profile, f.photo(), mapOf(tile.id to image))
            val savedTile = profileTiles(JSONObject(first.profile)).single()
            image.delete()
            assertArrayEquals(byteArrayOf(71, 73, 70, 56, 57, 97, 1, 0, 1, 0), favorites.image(savedTile.image)!!.readBytes())
            assertTrue(first.cover!!.isFile)
            profile.put("id", "session-two")
            assertEquals(first.id, favorites.key(profile))
            favorites.save(JSONObject(first.profile))
            assertEquals(1, favorites.items.value.size)
            assertTrue(favorites.items.value.single().cover!!.isFile)
            favorites.remove(first.id); assertTrue(favorites.items.value.isEmpty())
        } finally { f.scope.cancel() }
    }
    @Test fun malformedPublicTilesAreBoundedAndCannotBecomeFilePaths() {
        val json = JSONObject().put("tags", JSONArray((1..30).map { "x".repeat(50) }))
            .put("tiles", JSONArray().put("bad").put(JSONObject().put("id", "a").put("image", "../../secret").put("columns", 100).put("rows", -1)))
        val person = PublicIdentityProfile.from(json)
        assertEquals(1, person.tags.size); assertEquals(20, person.tags.single().length)
        assertEquals("", person.tiles.single().image); assertEquals(4, person.tiles.single().columns); assertEquals(1, person.tiles.single().rows)
    }
}
