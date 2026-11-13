package com.kigtts.next

import com.kigtts.next.media.EmbeddedPhotoOriginal
import com.kigtts.next.media.MotionPhoto
import com.kigtts.next.media.MotionPhotoWriter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmbeddedPhotoOriginalTest {
    @get:Rule val folder = TemporaryFolder()
    private fun jpeg() = folder.newFile("cover.jpg").apply {
        writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
    }

    @Test fun segmentedOriginalAndMotionTrailerAreIndependent() {
        val still = jpeg()
        val video = folder.newFile("clip.mp4").apply {
            DataOutputStream(outputStream()).use { it.writeInt(16); it.writeBytes("ftypmp42"); it.writeInt(0) }
        }
        val motion = MotionPhotoWriter.write(still, video, File(folder.root, "sourceMP.jpg"), 1000)
        val original = folder.newFile("raw.jpg").apply { writeBytes(ByteArray(200_000) { (it * 17).toByte() }) }
        val container = folder.newFile("combined.jpg")
        val recipe = "中".repeat(5000)
        EmbeddedPhotoOriginal.write(motion, original, recipe, container)
        val restored = File(folder.root, "restored.jpg")
        assertEquals(recipe, EmbeddedPhotoOriginal.extract(container, restored)?.recipe)
        assertArrayEquals(original.readBytes(), restored.readBytes())
        val clip = File(folder.root, "restored.mp4")
        assertTrue(MotionPhoto.extract(container, clip))
        assertArrayEquals(video.readBytes(), clip.readBytes())

        RandomAccessFile(container, "rw").use {
            it.seek(motion.length() - video.length() - 2 + 20_000)
            val byte = it.readUnsignedByte()
            it.seek(it.filePointer - 1); it.writeByte(byte xor 1)
        }
        assertTrue(restored.delete())
        assertNull(EmbeddedPhotoOriginal.extract(container, restored))
        assertFalse(restored.exists())
    }

    @Test fun heifClosesTheFinalOpenEndedBoxAndKeepsItsPayload() {
        val heif = folder.newFile("source.heic").apply {
            DataOutputStream(outputStream()).use {
                it.writeInt(16); it.writeBytes("ftypheic"); it.writeInt(0)
                it.writeInt(0); it.writeBytes("mdat"); it.writeInt(0x12345678)
            }
        }
        val original = jpeg()
        val container = folder.newFile("embedded.heic")
        EmbeddedPhotoOriginal.write(heif, original, "{}", container)
        RandomAccessFile(container, "r").use {
            it.seek(16); assertEquals(12, it.readInt()); assertEquals(0x6d646174, it.readInt()); assertEquals(0x12345678, it.readInt())
        }
        val restored = File(folder.root, "restored.jpg")
        assertEquals("{}", EmbeddedPhotoOriginal.extract(container, restored)?.recipe)
        assertArrayEquals(original.readBytes(), restored.readBytes())
    }
}
