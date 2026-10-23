package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.*
import com.kigtts.plugin.runtime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PackageInstallerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun updatePrioritizesNewPermissionsAndKeepsPreviousPermissionsCollapsed() {
        val file = Files.createTempFile("installer-update-ui", ".kigplugin").toFile()
        val manifest = PackageManifest("test.plugin", "测试", "2.0", 2, "test.Plugin", files = emptyMap(),
            capabilities = listOf("network", "camera"), permissionPurposes = mapOf("network" to "获取图片素材", "camera" to "拍摄新照片"))
        val info = PackageInstallerInfo(PendingPackage(file, PackagePreview(manifest, "a".repeat(64))),
            previous = manifest.copy(version = "1.0", versionCode = 1, capabilities = listOf("network")))
        var complete by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PackageInstallerDialog(listOf(info), false, complete, null, install = { complete = true }, dismiss = {})
        } }
        compose.onNodeWithText("权限").performClick()
        compose.onNodeWithText("新增权限").assertIsDisplayed()
        compose.onNodeWithText("拍摄新照片").assertIsDisplayed()
        compose.onNodeWithText("获取图片素材").assertDoesNotExist()
        compose.onNodeWithText("已有权限 · 1").performClick()
        compose.onNodeWithText("获取图片素材").assertExists()
        compose.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag("installer-details"))).assertCountEquals(0)
        compose.onNodeWithText("更新插件").performClick()
        compose.onNodeWithText("插件已更新").assertIsDisplayed()
        compose.onNodeWithText("更新完成").assertIsDisplayed()
        file.delete()
    }
    @Test fun tabsScrollInternallyWhileActionsStayFixedAndInstallNeverOffersPermissionToggles() {
        val file = Files.createTempFile("installer-ui", ".kigplugin").toFile()
        val info = PackageInstallerInfo(PendingPackage(file, PackagePreview(PackageManifest("example.speech", "语音识别增强", "1.2.0", 12,
            "com.example.Plugin", capabilities = listOf("ui", "network", "quick-text-read"), files = emptyMap(),
            permissionPurposes = mapOf("network" to "在线获取识别结果", "quick-text-read" to "读取当前字幕")), "a".repeat(64))),
            description = "扩展语音识别和字幕功能", author = "Example Developer", licenseText = "MIT License\n" + "许可正文\n".repeat(150) + "许可末尾")
        var completed by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PackageInstallerDialog(listOf(info), false, completed, null, install = { completed = true }, dismiss = {})
        } }
        compose.onNodeWithText("插件安装器").assertDoesNotExist()
        compose.onNodeWithText("扩展语音识别和字幕功能").assertIsDisplayed()
        val installBounds = compose.onNodeWithText("安装插件").getUnclippedBoundsInRoot()
        compose.onNodeWithText("权限").performClick()
        compose.onNodeWithText("在线获取识别结果").assertIsDisplayed()
        compose.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag("installer-details"))).assertCountEquals(0)
        compose.onNodeWithContentDescription("安装详情：权限").assertIsSelected()
        assertEquals(installBounds, compose.onNodeWithText("安装插件").getUnclippedBoundsInRoot())
        compose.onNodeWithText("许可").performClick()
        compose.onNodeWithTag("installer-details").performTouchInput { swipeUp() }
        assertEquals(installBounds, compose.onNodeWithText("安装插件").getUnclippedBoundsInRoot())
        compose.onNodeWithText("安装插件").performClick()
        compose.onNodeWithText("插件已安装").assertIsDisplayed()
        compose.onNodeWithText("安装完成").assertIsDisplayed()
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.onNodeWithText("权限将在使用相关功能时申请").assertIsDisplayed()
        file.delete()
    }
}
