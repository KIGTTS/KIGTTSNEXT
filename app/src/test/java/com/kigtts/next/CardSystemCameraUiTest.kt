package com.kigtts.next

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.cards.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardSystemCameraUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun fullCameraReturnsItsNewPhotoDirectlyWithoutLaunchingAnyPicker() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA, SystemCameraPhotos.permission)
        app.getSharedPreferences("polaroid_camera", 0).edit().putBoolean("full-system-camera", false).commit()
        var sources: CardPhotoSources? = null
        var error: String? = null
        var selected = false
        compose.setContent { sources = rememberCardPhotoSources({ selected = true; assertTrue(it.fromCamera) }, { error = it },
            receiveSystemPhoto = { _, _, _ -> Uri.parse("content://media/external/images/media/101") }) }
        compose.runOnIdle { sources!!.camera() }
        val camera = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(camera)
        assertEquals(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, camera.intent.action)
        assertFalse(camera.intent.hasExtra(MediaStore.EXTRA_OUTPUT))
        assertTrue(camera.intent.extras == null || camera.intent.extras!!.isEmpty)
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(camera.requestCode, Activity.RESULT_CANCELED, Intent()) }
        compose.waitForIdle()
        assertNull("System camera must not launch a second photo picker", shadowOf(compose.activity).nextStartedActivityForResult)
        assertTrue(selected); assertNull(error)
    }
    @Test fun cancellingWithoutAnyNewPhotoReturnsToTheCallerWithoutOpeningAPicker() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA, SystemCameraPhotos.permission)
        app.getSharedPreferences("polaroid_camera", 0).edit().clear().commit()
        var sources: CardPhotoSources? = null
        var cancelled = 0
        compose.setContent { sources = rememberCardPhotoSources({ fail("No photograph was taken") }, { fail(it) },
            onCameraCancelled = { cancelled++ }, receiveSystemPhoto = { _, _, _ -> null }) }
        compose.runOnIdle { sources!!.camera() }
        val camera = shadowOf(compose.activity).nextStartedActivityForResult
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(camera.requestCode, Activity.RESULT_CANCELED, Intent()) }
        compose.waitForIdle()
        assertEquals(1, cancelled)
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
    }
    @Test fun autoReceiveRejectsOldImagesAndScreenshotsAndAcceptsTheNewDcimPhoto() {
        val started = 1_700_000_000_000L
        assertTrue(SystemCameraPhotos.eligible(101, 100, started / 1000, started, started, "DCIM/Camera/"))
        assertFalse(SystemCameraPhotos.eligible(99, 100, started / 1000, started, started, "DCIM/Camera/"))
        assertFalse(SystemCameraPhotos.eligible(101, 100, started / 1000 - 100, started, started, "DCIM/Camera/"))
        assertFalse(SystemCameraPhotos.eligible(101, 100, started / 1000, started - 100000, started, "DCIM/Camera/"))
        assertFalse(SystemCameraPhotos.eligible(101, 100, started / 1000, started, started, "DCIM/Screenshots/"))
        assertFalse(SystemCameraPhotos.eligible(101, 100, started / 1000, started, started, "Download/"))
    }
}
