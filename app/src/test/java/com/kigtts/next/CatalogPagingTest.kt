package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.PluginCatalogList
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class CatalogPagingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun draggingMovesThePageBeforeRequestAndRequestsOnlyAfterSettling() {
        var selected by mutableIntStateOf(0)
        val calls = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PluginCatalogList(ManagementList("category/$selected",
                listOf(ManagementItem("item$selected", "插件 $selected")),
                filters = (0..2).map { ManagementFilter("$it", "分类 $it", "select/$it", selected == it, "category") },
                style = ManagementListStyle.CATALOG), false, { null }, { action, _ ->
                calls += action; selected = action.substringAfter('/').toInt()
            }, {})
        } }
        val row = compose.onNodeWithTag("managed-item/item0")
        val before = row.getUnclippedBoundsInRoot()
        val list = compose.onNodeWithTag("managed-list/category/0")
        list.performTouchInput { down(Offset(width * .85f, height * .5f)); moveBy(Offset(-width * .4f, 0f), 250) }
        compose.waitForIdle()
        assertTrue("Content follows the finger before release", row.getUnclippedBoundsInRoot().left < before.left)
        assertTrue("No page request before release", calls.isEmpty())
        list.performTouchInput { moveBy(Offset(-width * .3f, 0f), 120); up() }
        compose.waitForIdle()
        assertEquals(listOf("select/1"), calls)
        compose.onNodeWithTag("managed-item/item1").assertIsDisplayed()
    }
}
