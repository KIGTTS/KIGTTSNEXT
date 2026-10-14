package com.kigtts.next

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Open both rails through the same handle gesture used on the device. */
internal fun ComposeContentTestRule.expandAlbumGroups() {
    val grip = onNodeWithTag("card-album-grip", useUnmergedTree = true).getUnclippedBoundsInRoot()
    val wide = grip.bottom - grip.top > grip.right - grip.left
    onNodeWithTag("card-album-handle").performTouchInput {
        down(center)
        moveBy(if (wide) Offset(-280f * density, 0f) else Offset(0f, -280f * density), 400)
        advanceEventTime(300)
        up()
    }
    waitForIdle()
    onNodeWithTag("card-albums").assertIsDisplayed()
}
