package com.kigtts.next.ui.cards.camera

import android.view.KeyEvent
import com.kigtts.next.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraShutterKeysTest {
    private fun key(action: Int, code: Int = KeyEvent.KEYCODE_VOLUME_DOWN, repeat: Int = 0) =
        KeyEvent(0, 0, action, code, repeat)

    @Test fun volumeKeysStayWithTheSystemOutsideTheCamera() {
        val keys = CameraShutterKeys()
        assertFalse(keys.dispatch(key(KeyEvent.ACTION_DOWN)))
        assertFalse(keys.dispatch(key(KeyEvent.ACTION_UP)))
    }

    @Test fun onePhysicalPressCapturesOnceAndConsumesItsRepeatsAndRelease() {
        val keys = CameraShutterKeys(); var photos = 0
        keys.register { photos++ }.use {
            for (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) {
                assertTrue(keys.dispatch(key(KeyEvent.ACTION_DOWN, code)))
                assertTrue(keys.dispatch(key(KeyEvent.ACTION_DOWN, code, 1)))
                assertTrue(keys.dispatch(key(KeyEvent.ACTION_DOWN, code, 2)))
                assertTrue(keys.dispatch(key(KeyEvent.ACTION_UP, code)))
            }
            assertEquals(2, photos)
            assertFalse(keys.dispatch(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        }
    }

    @Test fun leavingAfterShutterDownConsumesItsReleaseButRestoresTheNextVolumePress() {
        val keys = CameraShutterKeys(); var photos = 0
        val binding = keys.register { photos++ }
        assertTrue(keys.dispatch(key(KeyEvent.ACTION_DOWN))); binding.close()
        assertTrue(keys.dispatch(key(KeyEvent.ACTION_UP)))
        assertFalse(keys.dispatch(key(KeyEvent.ACTION_DOWN)))
        assertEquals(1, photos)
    }

    @Test fun aDepartingRegistrationCannotRemoveTheNewCameraHandler() {
        val keys = CameraShutterKeys(); var oldPhotos = 0; var newPhotos = 0
        val old = keys.register { oldPhotos++ }
        val current = keys.register { newPhotos++ }; old.close()
        assertTrue(keys.dispatch(key(KeyEvent.ACTION_DOWN)))
        assertEquals(0, oldPhotos); assertEquals(1, newPhotos)
        current.close(); keys.clearPressed()
        assertFalse(keys.dispatch(key(KeyEvent.ACTION_UP)))
    }

    @Test fun theActivityPlatformKeyHooksDeliverOneShutterAndDrainItsReleaseAfterTheCameraLeaves() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        var photos = 0
        val binding = activity.cameraShutterKeys.register { photos++ }
        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, key(KeyEvent.ACTION_DOWN)))
        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, key(KeyEvent.ACTION_DOWN, repeat = 1)))
        binding.close()
        assertTrue(activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, key(KeyEvent.ACTION_UP)))
        assertEquals(1, photos)
        assertFalse(activity.onKeyDown(KeyEvent.KEYCODE_A, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
    }
}
