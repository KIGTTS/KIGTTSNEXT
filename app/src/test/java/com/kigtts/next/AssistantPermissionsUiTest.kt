package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.settings.AssistantAppPermissionRows
import com.kigtts.plugin.runtime.PermissionAccess
import com.kigtts.plugin.runtime.PluginPermissions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class AssistantPermissionsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun permissionsCanBeChangedDirectlyAndUseExistingGrants() {
        val ledger = PluginPermissions(compose.activity.applicationContext)
        val owner = "tools.cloud-agent:test-publisher"
        ledger.set(owner, "network", PermissionAccess.ALLOW)
        compose.setContent {
            KigttsTheme(ThemeSettings()) {
                Column {
                    AssistantAppPermissionRows(ledger, owner, listOf("network", "overlay"),
                        mapOf("network" to "连接你设置的 AI 服务"), enabled = true)
                }
            }
        }

        compose.onNodeWithContentDescription("访问网络：允许").assertIsSelected()
        compose.onNodeWithContentDescription("访问网络：禁用").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("悬浮窗入口与气泡：询问").assertIsSelected()
        assertEquals(PermissionAccess.DENY, ledger.access(owner, "network"))
        assertEquals(PermissionAccess.ASK, ledger.access(owner, "overlay"))
    }
}
