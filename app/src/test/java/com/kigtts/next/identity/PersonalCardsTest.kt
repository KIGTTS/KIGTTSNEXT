package com.kigtts.next.identity

import android.graphics.BitmapFactory
import android.graphics.Color
import com.kigtts.next.CardPrintCoordinatorTest
import com.kigtts.next.cards.*
import com.kigtts.next.sharing.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PersonalCardsTest {
    @Test fun backgroundPresetsShareInformationButKeepTheirGradientAndSurviveRemovingAllPresets() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val identities = IdentityRepository(f.app, f.cards)
            val first = identities.create()
            val profile = first.copy(nickname = "同一个人", bio = "你好", contacts = listOf(ContactLink("QQ", "12345")))
            identities.save(profile)
            val next = identities.create()
            assertEquals(profile.nickname, next.nickname); assertEquals(profile.contacts, next.contacts)
            assertFalse(next.gradient)
            identities.save(next.copy(gradient = true, nickname = "新昵称"))
            val reload = IdentityRepository(f.app, f.cards)
            assertTrue(reload.items.value.all { it.nickname == "新昵称" && it.contacts == profile.contacts })
            assertFalse(reload.items.value.first { it.id == first.id }.gradient)
            assertTrue(reload.items.value.first { it.id == next.id }.gradient)
            reload.items.value.forEach { reload.remove(it.id) }
            val restored = IdentityRepository(f.app, f.cards).create()
            assertEquals("新昵称", restored.nickname); assertEquals(profile.contacts, restored.contacts)
        } finally { f.scope.cancel() }
    }
    @Test fun oldCardsUseSelectedProfileAndPreserveAllLegacyInformationDuringMigration() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val prefs = f.app.getSharedPreferences("personal-cards", 0)
            val old = JSONArray().put(JSONObject().put("id", "a").put("designId", "design-a").put("nickname", "以前的信息"))
                .put(JSONObject().put("id", "b").put("designId", "design-b").put("nickname", "正在用的信息")
                    .put("contacts", JSONArray().put(JSONObject().put("label", "微信").put("value", "example"))))
            prefs.edit().putString("items", old.toString()).putString("selected", "b").commit()
            val identities = IdentityRepository(f.app, f.cards)
            assertTrue(identities.items.value.all { it.nickname == "正在用的信息" && it.gradient })
            identities.select("a")
            assertEquals("正在用的信息", IdentityRepository(f.app, f.cards).current!!.nickname)
            identities.save(identities.current!!.copy(gradient = false))
            assertEquals(old.toString(), prefs.getString("legacy-items-v1", null))
            assertEquals(1, IdentityRepository(f.app, f.cards).current!!.contacts.size)
            assertFalse(JSONArray(prefs.getString("items", "[]")).getJSONObject(0).has("nickname"))
        } finally { f.scope.cancel() }
    }
    @Test fun editableBackgroundAndStickersPersistAndExportAsOneLandscapeCard() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val identities = IdentityRepository(f.app, f.cards)
            val first = identities.create(); val next = identities.create()
            val image = f.stickers.importResource(f.photo())
            val design = f.cards.cards.value.first { it.id == first.designId }
            f.cards.update(design.copy(frameColor = 0xFF3F6A62, layers = listOf(CardLayer(kind = CardLayerKind.IMAGE, imageId = image.id, x = .75, y = .25, width = .2))))
            identities.save(first.copy(nickname = "测试名片", bio = "你好", contacts = listOf(ContactLink("微信", "example"))))
            identities.select(first.id)
            val reloaded = IdentityRepository(f.app, f.cards)
            assertEquals(first.id, reloaded.current?.id); assertEquals(listOf(first.id, next.id), reloaded.items.value.map { it.id })
            val paper = PolaroidRenderer.renderBitmap(f.stickers, f.cards.cards.value.first { it.id == first.designId })
            try {
                assertEquals(16.0 / 9, paper.width.toDouble() / paper.height, .002)
                assertEquals(0xFF3F6A62.toInt(), paper.getPixel(0, 0))
                val pixel = paper.getPixel((paper.width * .75).toInt(), (paper.height * .25).toInt())
                assertTrue(Color.blue(pixel) > 240 && Color.red(pixel) < 10)
            } finally { paper.recycle() }
            val export = exportIdentityCard(reloaded.current!!, f.stickers, f.app.cacheDir)
            assertTrue(export.length() > 0)
            val composed = BitmapFactory.decodeFile(export.path); assertTrue(composed.width > composed.height); composed.recycle()
            val media = FacePhotoRepository(f.cards, identities)
            val assets = media.prepare(listOf(first.designId), false, true, File(f.app.cacheDir, "identity-share"))
            assertEquals("BANNER", assets.single().format)
            media.receive(FaceOffer(faceToken(), faceToken(), first.publicJson(), assets), assets)
            val received = f.cards.cardsInAlbum("face-received").single()
            assertEquals(16.0 / 9, polaroidPaperGeometry(received).aspectRatio, .002)
        } finally { f.scope.cancel() }
    }
    @Test fun nativeReceivePreservesOriginalAndComposedVersionsAndNeverDuplicatesACard() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val original = f.photo(); val composed = f.photo()
            val photo = faceToken()
            fun asset(file: File, variant: String) = FaceAsset(faceToken(), photo, variant, "photo.jpg", "image/jpeg", file.length(), sha256(file), "WIDE", file)
            val assets = listOf(asset(original, "original"), asset(composed, "polaroid"))
            val identities = IdentityRepository(f.app, f.cards); val repository = FacePhotoRepository(f.cards, identities)
            val offer = FaceOffer(faceToken(), faceToken(), JSONObject().put("nickname", "对方"), assets)
            repository.receive(offer, assets); repository.receive(offer, assets)
            val received = f.cards.cardsInAlbum("face-received").single()
            assertTrue(f.cards.isReadOnly(received.id)); assertEquals("received.wide", received.frameStyleId)
            assertArrayEquals(original.readBytes(), f.stickers.imageFile(received.originalImageId!!)!!.readBytes())
            assertArrayEquals(composed.readBytes(), f.stickers.imageFile(received.imageId!!)!!.readBytes())
            assertEquals(1.0, polaroidPaperGeometry(received, f.stickers.frameStyle(received)).let { it.photoWidth / it.paperWidth }, 0.0)
        } finally { f.scope.cancel() }
    }
}
