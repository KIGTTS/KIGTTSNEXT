package com.kigtts.next

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.provider.MediaStore
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
    @Test fun fullCameraDoesNotUseRestrictedCaptureIntentOrLensExtrasAndReturnsToExplicitPicker() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        app.getSharedPreferences("polaroid_camera", 0).edit().clear().commit()
        var sources: CardPhotoSources? = null
        var error: String? = null
        var selected = false
        compose.setContent { sources = rememberCardPhotoSources({ selected = true }, { error = it }) }
        compose.runOnIdle { sources!!.camera() }
        val camera = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(camera)
        assertEquals(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, camera.intent.action)
        assertFalse(camera.intent.hasExtra(MediaStore.EXTRA_OUTPUT))
        assertTrue(camera.intent.extras == null || camera.intent.extras!!.isEmpty)
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(camera.requestCode, Activity.RESULT_CANCELED, Intent()) }
        val picker = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull("Returning from the full camera must request an explicit photo selection", picker)
        assertNotEquals(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, picker.intent.action)
        assertFalse(selected); assertNull(error)
    }
}
