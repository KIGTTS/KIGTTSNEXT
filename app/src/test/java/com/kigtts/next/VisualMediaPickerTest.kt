package com.kigtts.next

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.media.VisualMediaPicker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class VisualMediaPickerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test @Config(sdk = [29])
    fun olderAndroidUsesSystemDocumentsAndFiltersMedia() {
        val images = VisualMediaPicker().createIntent(context, PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, images.action)
        assertEquals("image/*", images.type)
        assertTrue(images.hasCategory(Intent.CATEGORY_OPENABLE))
        val mixed = VisualMediaPicker().createIntent(context, PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
        assertArrayEquals(arrayOf("image/*", "video/*"), mixed.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test @Config(sdk = [35])
    fun recentAndroidUsesNativePhotoPicker() {
        val intent = VisualMediaPicker().createIntent(context, PickVisualMediaRequest(PickVisualMedia.VideoOnly))
        assertEquals(MediaStore.ACTION_PICK_IMAGES, intent.action)
        assertEquals("video/*", intent.type)
    }
}
