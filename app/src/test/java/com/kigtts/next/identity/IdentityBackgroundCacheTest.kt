package com.kigtts.next.identity

import android.graphics.*
import com.kigtts.next.CardPrintCoordinatorTest
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [26, 35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdentityBackgroundCacheTest {
    @Test fun losslessBakePreservesFineDetailsAndReopensFromDiskAtDisplayResolution() = runBlocking {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val source = File(f.app.cacheDir, "detail.png")
            val photo = Bitmap.createBitmap(2048, 1152, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(photo); val paint = Paint()
            repeat(512) { x -> paint.color = if (x % 2 == 0) Color.WHITE else Color.BLACK
                canvas.drawRect(x * 4f, 0f, x * 4f + 4, 1152f, paint) }
            source.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }; photo.recycle()
            val image = f.stickers.importResource(source)
            val card = PolaroidCard(title = "", frameStyleId = "identity.banner", imageId = image.id)
            val person = IdentityCard(designId = card.id)
            val cache = f.stickers.identityBackgrounds
            val bake = cache.load(card, person, 1152)
            assertEquals(1152, bake.height); assertEquals(2048, bake.width)
            for (x in 0 until 512) assertEquals("4px条纹不得被缩略图放大糊掉", if (x % 2 == 0) Color.WHITE else Color.BLACK, bake.getPixel(x * 4 + 2, 500))
            assertSame(bake, cache.peek(card, person, 1152))
            assertSame(bake, cache.load(card, person.copy(nickname = "新名字", avatarFrame = "flower"), 1152))
            val reopened = IdentityBackgroundCache(f.stickers).load(card, person, 1152)
            assertTrue("PNG预烘焙必须无损", bake.sameAs(reopened))
            assertFalse("新缓存的像素不可回收正在显示的旧帧", bake.isRecycled)
        } finally { f.scope.cancel() }
    }

    @Test fun foregroundEditsReuseTheBakeWhilePoseAndSourceChangesInvalidateIt() = runBlocking {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val image = f.stickers.importResource(f.photo())
            val card = PolaroidCard(title = "", frameStyleId = "identity.banner", imageId = image.id)
            val person = IdentityCard(designId = card.id)
            val cache = f.stickers.identityBackgrounds; val key = cache.key(card, person)
            assertEquals(key, cache.key(card.copy(title = "文字", layers = listOf(CardLayer(kind = CardLayerKind.TEXT, text = "贴纸"))), person.copy(bio = "简介")))
            assertNotEquals(key, cache.key(card.copy(photoX = .7), person))
            assertNotEquals(key, cache.key(card.copy(photoStyle = PolaroidPhotoStyle(filter = "BW")), person))
            assertNotEquals(key, cache.key(card, person.copy(gradient = true)))
            assertNotEquals(key, cache.key(card, person.copy(pattern = "grid")))
            val source = requireNotNull(f.stickers.imageFile(image.id))
            assertTrue(source.setLastModified(source.lastModified() + 2000))
            assertNotEquals(key, cache.key(card, person))
        } finally { f.scope.cancel() }
    }

    @Test fun preparedEditorDrawsNewPosesImmediatelyWithoutReadingTheFileAgain() = runBlocking {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val image = f.stickers.importResource(f.photo())
            val card = PolaroidCard(title = "", frameStyleId = "identity.banner", imageId = image.id, photoBackdrop = PhotoBackdropMode.GRAY)
            var decodes = 0
            val cache = IdentityBackgroundCache(f.stickers) { _, _ ->
                decodes++
                Bitmap.createBitmap(46, 62, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            }
            val painter = cache.prepare(card, IdentityCard(designId = card.id))
            val pixels = Bitmap.createBitmap(400, 225, Bitmap.Config.ARGB_8888)
            painter.draw(Canvas(pixels), 400f, 225f, card)
            assertTrue(Color.blue(pixels.getPixel(200, 100)) >= 250)
            painter.draw(Canvas(pixels), 400f, 225f, card.copy(photoX = .9))
            assertEquals("同一次手势更新就应离开原位置", 0xFFB0B0B0.toInt(), pixels.getPixel(200, 100))
            assertTrue(Color.blue(pixels.getPixel(350, 100)) >= 250); pixels.recycle()
            assertEquals("每帧姿态变化不可重新解码", 1, decodes)
            cache.prepare(card.copy(photoX = .9), IdentityCard(designId = card.id))
            assertEquals("准备新姿态也应复用照片素材", 1, decodes)
            val filtered = card.copy(photoStyle = PolaroidPhotoStyle(filter = "BW"))
            val treated = cache.prepare(filtered, IdentityCard(designId = card.id))
            val gray = Bitmap.createBitmap(400, 225, Bitmap.Config.ARGB_8888)
            try {
                treated.draw(Canvas(gray), 400f, 225f, filtered)
                val color = gray.getPixel(200, 100)
                assertEquals(Color.red(color), Color.blue(color))
            } finally { gray.recycle() }
            assertEquals("作为名片背景的拍立得也应用当前滤镜", 2, decodes)
        } finally { f.scope.cancel() }
    }
}
