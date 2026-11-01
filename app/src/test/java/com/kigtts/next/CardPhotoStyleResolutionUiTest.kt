package com.kigtts.next

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import com.kigtts.next.ui.cards.rememberCardPhotoStyle
import com.kigtts.plugin.api.PolaroidPhotoStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class CardPhotoStyleResolutionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun treatmentReachesDecodedResolutionAndLodUpgradeNeverFallsBackToThumbnail() {
        val low = Bitmap.createBitmap(1536, 1024, Bitmap.Config.ARGB_8888)
        val high = Bitmap.createBitmap(2048, 1536, Bitmap.Config.ARGB_8888)
        var image by mutableStateOf(low.asImageBitmap())
        var edge = 0
        val observed = mutableListOf<Int>()
        compose.setContent {
            val styled = rememberCardPhotoStyle(image, "one-photo", PolaroidPhotoStyle(filter = "BW"), { null })
            SideEffect { edge = styled?.let { maxOf(it.width, it.height) } ?: 0; observed += edge }
        }
        compose.waitUntil(8000) { edge == 1536 }
        observed.clear()
        compose.runOnIdle { image = high.asImageBitmap() }
        compose.waitForIdle()
        compose.waitUntil(8000) { edge == 2048 }
        assertFalse(observed.toString(), observed.any { it in 1..512 })
        assertFalse(low.isRecycled); assertFalse(high.isRecycled)
    }
}
