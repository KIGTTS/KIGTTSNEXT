package com.kigtts.next

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.GalleryImages
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryImagesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var app: Application
    private lateinit var provider: Images
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        provider = Images(temporary.newFolder())
        provider.attachInfo(app, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
    }
    private fun png() = temporary.newFile("original.png").also { file ->
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun onlyPublishesAfterBytesAreCompleteAndKeepsTheOriginalEncoding() {
        val file = png(); val bytes = file.readBytes()
        val uri = GalleryImages(app).publish(file, "拍立得", 1234)
        assertEquals(listOf("pending", "write", "visible"), provider.events)
        assertEquals("Pictures/KIGTTS/拍立得", provider.values.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(128, provider.values.getAsInteger(MediaStore.Images.Media.WIDTH))
        assertEquals(1234L, provider.values.getAsLong(MediaStore.Images.Media.DATE_TAKEN))
        assertArrayEquals(bytes, provider.file(uri).readBytes()); assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun animatedGifIsCopiedWithoutFlattening() {
        val bytes = requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/profile/animated.gif")).use { it.readBytes() }
        val file = temporary.newFile("original.gif").apply { writeBytes(bytes) }
        val uri = GalleryImages(app).publish(file, "图片")
        assertEquals("image/gif", provider.values.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertTrue(provider.values.getAsString(MediaStore.Images.Media.DISPLAY_NAME).endsWith(".gif"))
        assertArrayEquals(bytes, provider.file(uri).readBytes())
    }
    @Test fun writeAndPublishFailuresRemovePartialMediaRows() {
        val file = png()
        listOf(true, false).forEach { writing ->
            provider.failWrite = writing; provider.failPublish = !writing
            assertThrows(Exception::class.java) { GalleryImages(app).publish(file, "图片") }
        }
        assertEquals(2, provider.deleted.size); assertTrue(provider.deleted.none { provider.file(it).exists() })
        assertTrue(file.isFile)
    }
    private class Images(private val directory: File) : ContentProvider() {
        var next = 0L; var values = ContentValues(); var failWrite = false; var failPublish = false
        val events = mutableListOf<String>(); val deleted = mutableListOf<Uri>()
        fun file(uri: Uri) = File(directory, uri.lastPathSegment!!)
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/png"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            this.values = ContentValues(values); events += "pending"
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ++next)
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            events += "write"; if (failWrite) throw IOException("failed write")
            return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE)
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            if (failPublish) return 0
            check(file(uri).length() > 0); events += "visible"; return 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int { deleted += uri; file(uri).delete(); return 1 }
    }
}
