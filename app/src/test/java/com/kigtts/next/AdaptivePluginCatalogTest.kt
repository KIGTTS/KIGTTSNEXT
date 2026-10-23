package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.ui.plugins.PluginCatalogList
import com.kigtts.next.ui.home.quickPhraseColumns
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h900dp-mdpi")
class AdaptivePluginCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun wideCatalogHasOneShortcutRowAndTwoPluginColumns() {
        compose.setContent { MaterialTheme { PluginCatalogList(catalog(12), false, { null }, { _, _ -> }, {}) } }
        val first = compose.onNodeWithTag("managed-item/p0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("managed-item/p1").getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top); assertTrue(second.left > first.right)
        val a = compose.onNodeWithTag("catalog-shortcut/categories").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("catalog-shortcut/all").getUnclippedBoundsInRoot()
        assertEquals(a.top, b.top); assertTrue(a.left >= 80.dp)
        compose.onNodeWithTag("catalog-tabs").assertExists()
        assertEquals(2, quickPhraseColumns(377f)); assertEquals(4, quickPhraseColumns(752f))
        assertEquals(6, quickPhraseColumns(2000f))
    }
    @Test fun phoneKeepsListAndAppendsOnlyOneBatchWhenTheTailAppears() {
        var count by mutableIntStateOf(12); val requests = mutableListOf<String>()
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) {
            MaterialTheme { Box(Modifier.width(393.dp).height(720.dp)) {
                PluginCatalogList(catalog(count), false, { null }, { action, _ -> requests += action; count += 12 }, {})
            } }
        } }
        val first = compose.onNodeWithTag("managed-item/p0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("managed-item/p1").getUnclippedBoundsInRoot()
        assertTrue(second.top > first.bottom)
        compose.onNodeWithTag("catalog-shortcut/all").assertExists()
        compose.onNodeWithTag("managed-list/catalog").performScrollToIndex(13)
        compose.waitForIdle()
        assertEquals(listOf("more/12"), requests)
        compose.onNodeWithTag("managed-list/catalog").performScrollToNode(hasTestTag("managed-item/p23"))
        compose.onNodeWithTag("managed-item/p23").assertExists()
    }
    private fun catalog(count: Int) = ManagementList("catalog",
        (0 until count).map { ManagementItem("p$it", "插件 $it", "插件介绍", PluginIcon.SETTINGS) },
        filters = listOf(ManagementFilter("all", "全部", "filter/all", true)),
        style = ManagementListStyle.CATALOG,
        shortcuts = listOf("categories", "sources", "all").map { PageAction(it, it, PluginIcon.SETTINGS) },
        loadMore = if (count < 24) ManagementLoadMore("more/$count") else null)
}
