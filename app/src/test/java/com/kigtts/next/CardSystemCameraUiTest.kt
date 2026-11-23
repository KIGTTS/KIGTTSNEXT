package com.kigtts.next

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.cards.*
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.KigttsApp
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
    @Test fun homeSwipeLaunchesFullSystemCameraOnlyAfterSettlingAndReturnsHome() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("polaroid_camera", 0).edit().putBoolean("use-system-camera", true).commit()
        val model = testAppModel(app)
        compose.setContent { KigttsApp(model) }
        compose.waitForIdle()
        val activity = shadowOf(compose.activity)
        val root = compose.onRoot()
        compose.mainClock.autoAdvance = false
        root.performTouchInput {
            down(Offset(width * .8f, height * .62f))
            moveTo(Offset(width * .68f, height * .62f), 150)
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("system-camera-standby").assertExists()
        assertNull(activity.nextStartedActivityForResult)
        root.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(800)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("system-camera-standby").assertDoesNotExist()
        assertNull(activity.nextStartedActivityForResult)

        root.performTouchInput { swipeLeft(startX = width * .8f, endX = width * .2f, durationMillis = 400) }
        compose.onNodeWithTag("system-camera-standby").assertIsDisplayed()
        val camera = checkNotNull(activity.nextStartedActivityForResult)
        assertEquals(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, camera.intent.action)
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(camera.requestCode, Activity.RESULT_CANCELED, Intent()) }
        compose.onNodeWithTag("system-camera-standby").assertDoesNotExist()
        assertEquals(Destination.Home, model.state.value.destination)
        assertNull(activity.nextStartedActivityForResult)
    }

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
