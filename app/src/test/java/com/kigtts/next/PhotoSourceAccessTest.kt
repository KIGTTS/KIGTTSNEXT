package com.kigtts.next

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.WebAndroidPermissions
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35])
class PhotoSourceAccessTest {
    @get:Rule val temp=TemporaryFolder()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private val uri=Uri.parse("content://media/external/images/media/1")
    private fun jpeg(gps:Boolean):File = temp.newFile().also { file ->
        Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888).also { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
        }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2020:01:02 03:04:05")
            if(gps) setLatLong(31.23,121.47)
            saveAttributes()
        }
    }
    private class Photos(private val original:File,private val redacted:File):ContentProvider() {
        val requests=mutableListOf<Uri>()
        var rejectOriginal=false
        override fun onCreate()=true
        override fun getType(uri:Uri)="image/jpeg"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor?=null
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            requests+=uri
            val raw=uri.getQueryParameter("requireOriginal")=="1"
            if(raw&&rejectOriginal) throw SecurityException("Picker excludes location")
            return ParcelFileDescriptor.open(if(raw)original else redacted,ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }
    private fun provider():Photos=Photos(jpeg(true),jpeg(false)).also {
        it.attachInfo(app,ProviderInfo().apply { authority="media" })
        ShadowContentResolver.registerProviderInternal("media",it)
    }
    private suspend fun read():FrozenPhotoFacts {
        val copy=temp.newFile()
        openPhotoSource(app,uri).use { source -> copy.outputStream().use { target -> source.copyTo(target) } }
        return PolaroidPhotoFacts.read(copy,false)
    }
    @After fun cancel() { WebAndroidPermissions.cancel() }
    @Test fun grantedOriginalExifKeepsGpsAndCaptureDate()=runBlocking {
        val provider=provider();shadowOf(app).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)
        val facts=read()
        assertEquals(31.23,facts.latitude!!,.00001);assertEquals(121.47,facts.longitude!!,.00001)
        assertNotNull(facts.capturedAt);assertFalse(facts.fromCamera)
        assertEquals("1",provider.requests.single().getQueryParameter("requireOriginal"))
    }
    @Test fun refusingMetadataPermissionStillImportsTheSelectedPhoto()=runBlocking {
        val provider=provider();shadowOf(app).denyPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)
        val request=async(start=CoroutineStart.UNDISPATCHED) { read() }
        assertEquals(listOf(Manifest.permission.ACCESS_MEDIA_LOCATION),WebAndroidPermissions.pending.value?.permissions)
        WebAndroidPermissions.finish(mapOf(Manifest.permission.ACCESS_MEDIA_LOCATION to false))
        val facts=request.await()
        assertNull(facts.latitude);assertNotNull(facts.capturedAt)
        assertNull(provider.requests.single().getQueryParameter("requireOriginal"))
    }
    @Test fun aPickerThatExcludesGpsFallsBackWithoutBreakingImport()=runBlocking {
        val provider=provider().apply { rejectOriginal=true }
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)
        assertNull(read().latitude)
        assertEquals(2,provider.requests.size)
    }
}
