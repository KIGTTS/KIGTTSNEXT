package com.kigtts.next

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.sketch.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SketchAlbumRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var provider: ImagesProvider
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = temporary.newFolder("private")
        context = object : ContextWrapper(app) { override fun getExternalFilesDir(type: String?) = external }
        provider = ImagesProvider(temporary.newFolder("media"))
        provider.attachInfo(app, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    private fun document(): SketchDocument {
        val state = SketchState().apply { color = 0xFFFF0000L; penSize = 12f; initializeCanvas(IntSize(200, 400)) }
        state.sample(Offset(20f, 200f), 1f, false, false, 1f, 200f, 400f)
        state.sample(Offset(180f, 200f), 1f, false, false, 1f, 200f, 400f); state.endStroke()
        return state.document()
    }

    @Test fun savingPublishesACompleteSystemImageAndReopensAnIndependentEditableArtwork() {
        val repo = SketchAlbumRepository(context); val drawing = document(); val work = repo.save(drawing)
        assertEquals(listOf("pending", "write", "visible"), provider.events)
        assertEquals("Pictures/KIGTTS/画板", provider.inserted.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(1, provider.inserted.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        val restored = SketchAlbumRepository(context)
        assertEquals(listOf(work), restored.list()); assertEquals(drawing, restored.load(work.id))
        listOf(restored.imageFile(work.id), provider.file(Uri.parse(work.mediaUri))).forEach { file ->
            val bitmap = requireNotNull(file.inputStream().use { BitmapFactory.decodeStream(it) })
            try {
                assertEquals(220, bitmap.width); assertEquals(60, bitmap.height)
                assertEquals(Color.RED, bitmap.getPixel(110, 30)); assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
            }
            finally { bitmap.recycle() }
        }
        repo.remove(work.id)
        assertTrue(repo.list().isEmpty()); assertEquals(listOf(Uri.parse(work.mediaUri)), provider.deleted)
    }

    @Test fun failuresWhileWritingOrPublishingRollBackBothMediaRowsAndUncommittedLocalArtwork() {
        listOf(true, false).forEach { failWrite ->
            provider.failWrite = failWrite; provider.failCommit = !failWrite
            val repo = SketchAlbumRepository(context)
            assertThrows(Exception::class.java) { repo.save(document()) }
            assertTrue(repo.list().isEmpty())
            val works = File(context.getExternalFilesDir(null), "appdata/Kigtts/sketch/works")
            assertTrue(works.listFiles().orEmpty().isEmpty())
        }
        assertEquals(2, provider.deleted.size)
    }

    @Test fun repeatedDraftWritesAreAtomicAndUnknownIdsNeverDeleteOrOpenOtherMedia() {
        val repo = SketchAlbumRepository(context); val first = document(); val empty = first.copy(strokes = emptyList())
        repo.saveDraft(first); repo.saveDraft(empty)
        assertEquals(empty, SketchAlbumRepository(context).loadDraft())
        assertThrows(IllegalArgumentException::class.java) { repo.remove("../foreign") }
        assertThrows(IllegalArgumentException::class.java) { repo.load("00000000-0000-0000-0000-000000000000") }
        assertTrue(provider.deleted.isEmpty())
    }

    private class ImagesProvider(private val directory: File) : ContentProvider() {
        var next = 0L; var failWrite = false; var failCommit = false
        var inserted = ContentValues(); val events = mutableListOf<String>(); val deleted = mutableListOf<Uri>()
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/png"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            inserted = ContentValues(requireNotNull(values)); events += "pending"
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ++next)
        }
        fun file(uri: Uri) = File(directory, requireNotNull(uri.lastPathSegment) + ".png")
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            events += "write"; if (failWrite) throw IOException("provider write failure")
            return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE)
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            check(values?.getAsInteger(MediaStore.Images.Media.IS_PENDING) == 0)
            if (failCommit) return 0
            check(file(uri).length() > 0); events += "visible"; return 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted += uri; file(uri).delete(); return 1
        }
    }
}
