package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.ui.management.*
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.plugin.api.ManagementItem
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35])
class ManagedItemCornersTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun movingFromFirstToLastInterpolatesBothEndsInsteadOfSnapping() {
        var position by mutableStateOf(SettingPosition.FIRST); var top = 0f; var bottom = 0f
        compose.setContent { MaterialTheme {
            val density = LocalDensity.current; val shape = managedItemShape(position, false)
            SideEffect { top = shape.topStart.toPx(Size.Zero, density) / density.density
                bottom = shape.bottomStart.toPx(Size.Zero, density) / density.density }
            Box {}
        } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { position = SettingPosition.LAST }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        assertTrue("top=$top bottom=$bottom", top > 4f && top < 20f)
        assertTrue("top=$top bottom=$bottom", bottom > 4f && bottom < 20f)
        compose.mainClock.advanceTimeBy(240); compose.waitForIdle()
        assertEquals(4f, top, .1f); assertEquals(20f, bottom, .1f)
    }
    @Test fun pinnedSectionsStartAFreshTabletRowAndKeepTheirOwnConnectedCorners() {
        val pinned = ManagementItem("p", "置顶", pinned = true)
        val normals = (1..4).map { ManagementItem("n$it", "普通") }
        val sections = managedItemSections(listOf(pinned) + normals)
        assertEquals(listOf(listOf(pinned), normals), sections)
        assertEquals(SettingPosition.SINGLE, managedGridPosition(sections[0], 0, 2))
        assertEquals(SettingPosition.FIRST, managedGridPosition(sections[1], 0, 2))
        assertEquals(SettingPosition.FIRST, managedGridPosition(sections[1], 1, 2))
        assertEquals(SettingPosition.LAST, managedGridPosition(sections[1], 2, 2))
        assertEquals(SettingPosition.LAST, managedGridPosition(sections[1], 3, 2))
    }
}
