package com.kigtts.next.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class HomeSwipeExclusionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun aProtectedRegionStillAllowsSwipesOutsideIt() {
        var opened = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { HomeSideDrawer(scope, { opened++ }, {}) }
            val exclusions = remember { HomeSwipeExclusions() }
            CompositionLocalProvider(LocalHomeSwipeExclusions provides exclusions) {
                Column(Modifier.fillMaxSize().homeSideDrawer(state) { true }) {
                    Box(Modifier.fillMaxWidth().height(100.dp).excludeHomeSideSwipes())
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("other-surface"))
                }
            }
        }
        compose.onNodeWithTag("other-surface").performTouchInput { swipe(center + Offset(80f, 0f), center - Offset(80f, 0f)) }
        compose.runOnIdle { assertEquals(1, opened) }
    }
    @Test fun excludedWebRegionDoesNotDisableSideNavigationElsewhereAndCanReleaseItsLease() {
        var protected by mutableStateOf(true); var opened = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { HomeSideDrawer(scope, { opened++ }, {}) }
            val exclusions = remember { HomeSwipeExclusions() }
            CompositionLocalProvider(LocalHomeSwipeExclusions provides exclusions) {
                Column(Modifier.fillMaxSize().homeSideDrawer(state) { true }) {
                    Box(Modifier.fillMaxWidth().height(200.dp).testTag("web-surface").excludeHomeSideSwipes(protected))
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("other-surface"))
                }
            }
        }
        compose.onNodeWithTag("web-surface").performTouchInput { swipe(center + Offset(80f, 0f), center - Offset(80f, 0f)) }
        compose.runOnIdle { assertEquals(0, opened); protected = false }
        compose.onNodeWithTag("web-surface").performTouchInput { swipe(center + Offset(80f, 0f), center - Offset(80f, 0f)) }
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
