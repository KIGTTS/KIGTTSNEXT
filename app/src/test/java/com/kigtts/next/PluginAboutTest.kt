package com.kigtts.next

import com.kigtts.next.ui.plugins.installedPresentation
import com.kigtts.plugin.runtime.PackageManifest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginAboutTest {
    @Test fun aboutReadsOnlyBoundedSignedPresentationAndLicenses() {
        val root = Files.createTempDirectory("plugin-about-").toFile()
        try {
            File(root, "assets").mkdirs()
            File(root, "assets/plugin-info.json").writeText("""{"description":"包内介绍","author":"开发者","homepage":"https://example.com"}""")
            File(root, "LICENSE.md").writeText("许可正文")
            File(root, "assets/about.md").writeText("不应读取的额外文件")
            val manifest = PackageManifest("test.about", "测试插件", "1", 1, "Test", files = mapOf("assets/plugin-info.json" to "hash", "LICENSE.md" to "hash"))
            val info = installedPresentation(root, manifest)
            assertEquals("包内介绍", info.description); assertEquals("开发者", info.author); assertTrue(info.licenseText.contains("许可正文"))
            val unsigned = installedPresentation(root, manifest.copy(files = emptyMap()))
            assertEquals("", unsigned.description); assertEquals("", unsigned.licenseText)
            File(root, "assets/plugin-info.json").writeText("x".repeat(20_000))
            assertEquals("", installedPresentation(root, manifest).description)
        } finally { root.deleteRecursively() }
    }
}
