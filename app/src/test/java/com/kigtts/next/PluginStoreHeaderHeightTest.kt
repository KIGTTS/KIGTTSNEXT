package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.plugins.CatalogSectionAction
import com.kigtts.next.ui.settings.SettingsGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PluginStoreHeaderHeightTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun singleLineAndSummaryGroupsMatchSettingsEvenWithMoreAction() {
        var calls = 0
        compose.setContent { MaterialTheme {
            Column {
                for (summary in listOf("", "有备注的分组")) {
                    val kind = if (summary.isEmpty()) "single" else "summary"
                    SettingsGroup("设置", summary) { Box(Modifier.fillMaxWidth().height(40.dp).testTag("settings/$kind")) }
                    SettingsGroup("商店", summary, headerAction = { CatalogSectionAction("更多") { calls++ } }) {
                        Box(Modifier.fillMaxWidth().height(40.dp).testTag("store/$kind"))
                    }
                }
            }
        } }
        for (kind in listOf("single", "summary")) {
            // Both have identical content: comparing parents includes the complete header and padding.
            val settings = compose.onNodeWithTag("settings/$kind").onParent().onParent().getUnclippedBoundsInRoot()
            val store = compose.onNodeWithTag("store/$kind").onParent().onParent().getUnclippedBoundsInRoot()
            assertEquals(settings.bottom - settings.top, store.bottom - store.top)
        }
        compose.onAllNodesWithText("更多")[0].performClick()
        assertEquals(1, calls)
    }
}
