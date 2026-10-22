package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.*
import com.kigtts.plugin.runtime.*
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
class PackageInstallerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(dark: Boolean) {
        val file = Files.createTempFile("installer-layout", ".kigplugin").toFile()
        val info = PackageInstallerInfo(PendingPackage(file, PackagePreview(PackageManifest("example.speech", "语音识别增强", "1.2.0", 12,
            "com.example.Plugin", capabilities = listOf("network", "quick-text-read"), files = emptyMap(),
            permissionPurposes = mapOf("network" to "在线获取识别结果", "quick-text-read" to "用于处理应用内的字幕文本")), "a".repeat(64))),
            description = "扩展语音识别功能，提供多语言识别和字幕输出能力，可与现有字幕系统配合使用。", author = "Example Developer", licenseText = "MIT License\n\n" + "许可正文\n".repeat(80))
        compose.setContent { KigttsTheme(ThemeSettings(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
            PackageInstallerDialog(listOf(info), false, false, null, install = {}, dismiss = {})
        } }
        compose.onNodeWithText("安装插件").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("取消").assertIsDisplayed()
        java.io.File("build/previews").mkdirs()
        compose.onRoot().captureRoboImage("build/previews/installer-${if (dark) "landscape-dark" else "portrait-light"}.png")
        compose.onNodeWithText("权限").performClick()
        compose.onNodeWithText("安装插件").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/installer-permissions-${if (dark) "landscape-dark" else "portrait-light"}.png")
        file.delete()
    }
    @Test fun portraitLayout() = show(false)
    @Test @Config(qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi") fun landscapeLayout() = show(true)
}
