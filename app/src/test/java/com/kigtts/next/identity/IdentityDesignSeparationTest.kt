package com.kigtts.next.identity

import com.kigtts.next.CardPrintCoordinatorTest
import com.kigtts.plugin.api.*
import kotlinx.coroutines.cancel
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdentityDesignSeparationTest {
    @Test fun migratesOldCardBackgroundBeforeRemovingItsAlbumEntryAndKeepsPhotos() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val photo = f.cards.create(PolaroidCard(title = "保留照片"))
            f.stickers.apply(listOf(StickerEdit.PutFrameStyle(PolaroidFrameStyle("identity.banner", "名片", format = PolaroidFormat.WIDE))))
            val album = f.cards.createAlbum("我的名片", "personal-cards")
            val design = f.cards.create(PolaroidCard(title = "名片背景", frameStyleId = "identity.banner"), album.id)
            f.app.getSharedPreferences("personal-cards", 0).edit().putString("items", JSONArray().put(JSONObject()
                .put("id", "legacy-person").put("designId", design.id).put("nickname", "以前的昵称")).toString()).commit()
            val repo = IdentityRepository(f.app, f.cards)
            assertEquals("以前的昵称", repo.current!!.nickname)
            assertEquals(design, f.stickers.identityDesigns.get(design.id))
            assertEquals(listOf(photo.id), f.cards.cards.value.map { it.id })
            assertFalse(f.cards.albums.value.any { it.id == "personal-cards" })
            IdentityRepository(f.app, f.cards)
            assertEquals(1, f.stickers.identityDesigns.items.value.size)
            val saved = repo.current!!.copy(gradient = true, gradientColor = 0xFFFFCC00, pattern = "dots", cardFrameId = "petals", avatarFrame = "flower")
            repo.save(saved)
            val reload = IdentityRepository(f.app, f.cards).current!!
            assertEquals(saved, reload)
            assertTrue(reload.publicJson().getString("avatarClipPath").startsWith("M"))
            assertFalse(AvatarFrames.svgPath("flower") == AvatarFrames.svgPath("circle"))
        } finally { f.scope.cancel() }
    }
    @Test fun newPersonalBackgroundNeverAppearsInPhotoLibrary() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val repo = IdentityRepository(f.app, f.cards); val card = repo.create()
            assertTrue(f.cards.cards.value.isEmpty()); assertNotNull(f.stickers.identityDesigns.get(card.designId))
            assertFalse(card.gradient)
            repo.remove(card.id); assertTrue(f.stickers.identityDesigns.items.value.isEmpty())
        } finally { f.scope.cancel() }
    }
}
