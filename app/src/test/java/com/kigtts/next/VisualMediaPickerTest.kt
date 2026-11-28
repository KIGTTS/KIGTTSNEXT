package com.kigtts.next

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.media.VisualMediaPicker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class VisualMediaPickerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test @Config(sdk = [29])
    fun olderAndroidWithoutGalleryUsesContentPickerAndFiltersMedia() {
        val images = VisualMediaPicker().createIntent(context, PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        assertEquals(Intent.ACTION_GET_CONTENT, images.action)
        assertEquals("image/*", images.type)
        assertTrue(images.hasCategory(Intent.CATEGORY_OPENABLE))
        val mixed = VisualMediaPicker().createIntent(context, PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
        assertEquals(Intent.ACTION_GET_CONTENT, mixed.action)
        assertArrayEquals(arrayOf("image/*", "video/*"), mixed.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test @Config(sdk = [29])
    fun olderAndroidOpensTheSystemGalleryEvenWithPlayServicesAndOurAlbumInstalled() {
        val manager = shadowOf(context.packageManager)
        val galleryIntent = Intent(Intent.ACTION_PICK).setType("image/*")
        fun register(intent: Intent, app: String, name: String) {
            manager.addResolveInfoForIntent(intent, ResolveInfo().apply {
                isDefault = true
                activityInfo = ActivityInfo().apply {
                    packageName = app
                    this.name = name
                    exported = true
                    applicationInfo = ApplicationInfo().apply {
                        packageName = app
                        flags = ApplicationInfo.FLAG_SYSTEM
                    }
                }
            })
        }
        register(galleryIntent, context.packageName, "AlbumActivity")
        register(galleryIntent, "com.google.android.gms", "Picker")
        register(Intent("androidx.activity.result.contract.action.PICK_IMAGES"), "com.google.android.gms", "Picker")
        register(galleryIntent, "com.android.gallery3d", "ThirdSelectSlotAlbumActivity")
        val result = VisualMediaPicker().createIntent(context, PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        assertEquals(Intent.ACTION_PICK, result.action)
        assertEquals("com.android.gallery3d", result.component?.packageName)
    }

    @Test @Config(sdk = [35])
    fun recentAndroidUsesNativePhotoPicker() {
        val intent = VisualMediaPicker().createIntent(context, PickVisualMediaRequest(PickVisualMedia.VideoOnly))
        assertEquals(MediaStore.ACTION_PICK_IMAGES, intent.action)
        assertEquals("video/*", intent.type)
    }
}
