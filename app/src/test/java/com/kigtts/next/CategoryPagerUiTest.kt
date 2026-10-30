package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.home.CategoryContentMotion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CategoryPagerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun horizontalCardScrollingCannotAdvanceTheVerticalCategoryPager() {
        var selected by mutableStateOf("一")
        compose.setContent {
            CategoryContentMotion(selected, listOf("一", "二"), true, { selected = it },
                Modifier.size(320.dp, 100.dp), vertical = true) { name, _ ->
                LazyRow(Modifier.fillMaxSize().testTag("row-$name"), state = rememberLazyListState()) {
                    items(12) { Text("卡片 $it", Modifier.size(150.dp, 100.dp)) }
                }
            }
        }
        compose.onNodeWithTag("row-一").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("row-一").assertIsDisplayed()
        assertEquals("一", selected)
    }
    @Test fun pageFollowsTheHeldFingerBeforeSelectionChangesAndSettlesAfterRelease() {
        var selected by mutableStateOf("一")
        compose.setContent { CategoryContentMotion(selected, listOf("一", "二"), true, { selected = it }, Modifier.size(320.dp, 400.dp)) { name, _ -> Text(name) } }
        val before = compose.onNodeWithTag("category-page-一").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("category-content-transition").performTouchInput {
            down(Offset(width * .8f, height / 2f)); moveTo(Offset(width * .2f, height / 2f), delayMillis = 200)
        }
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        val during = compose.onNodeWithTag("category-page-一").getUnclippedBoundsInRoot()
        assertTrue("The held page must move with the finger", during.left < before.left - 100.dp)
        assertEquals("一", selected)
        compose.onNodeWithTag("category-content-transition").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1200); compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("二", selected)
        compose.onNodeWithTag("category-content-transition").performTouchInput { swipeLeft() }
        assertEquals("二", selected)
    }
}
