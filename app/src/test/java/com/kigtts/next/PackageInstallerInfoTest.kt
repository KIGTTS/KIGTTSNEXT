package com.kigtts.next

import com.kigtts.next.ui.plugins.*
import com.kigtts.plugin.runtime.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.*

class PackageInstallerInfoTest {
    @Test fun updatePermissionsCompareDeclarationsAndKeepRemovedPermissionsOut() {
        val file = Files.createTempFile("installer-update", ".zip").toFile()
        try {
            val next = PackageManifest("test.plugin", "测试", "2.0", 2, "test.Plugin", files = emptyMap(), capabilities = listOf("ui", "network", "camera"))
            val previous = next.copy(version = "1.0", versionCode = 1, capabilities = listOf("ui", "network", "microphone"))
            val info = PackageInstallerInfo(PendingPackage(file, PackagePreview(next, "a".repeat(64))), previous = previous)
            assertEquals(listOf("camera"), info.addedPermissions)
            assertEquals(listOf("network"), info.existingPermissions)
            assertEquals("更新插件", installerLabels(listOf(info)).action)
            assertEquals("插件已更新", installerLabels(listOf(info)).result)
            assertEquals("安装并更新", installerLabels(listOf(info, info.copy(previous = null))).action)
        } finally { file.delete() }
    }
    @Test fun onlyBoundedManifestListedPresentationAssetsAreReadAndUiIsNotAPermission() {
        val file = Files.createTempFile("installer-info", ".zip").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                mapOf("assets/plugin-info.json" to """{"description":"带你认识更多声音","author":"Example","symbol":"graphic_eq"}""",
                    "LICENSE" to "MIT License", "assets/licenses/NOTICE.md" to "第三方许可", "README.md" to "unlisted data").forEach { (path, text) ->
                    zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            fun candidate(files: Map<String, String>) = PendingPackage(file, PackagePreview(PackageManifest("example.plugin", "示例", "1.0", 1,
                "com.example.Plugin", files = files, capabilities = listOf("ui", "network", "network", "camera")), "a".repeat(64)))
            val info = installerInfo(candidate(mapOf("assets/plugin-info.json" to "hash", "LICENSE" to "hash", "assets/licenses/NOTICE.md" to "hash")))
            assertEquals("带你认识更多声音", info.description); assertEquals("graphic_eq", info.symbol)
            assertTrue(info.licenseText.contains("MIT License")); assertTrue(info.licenseText.contains("第三方许可"))
            assertEquals(listOf("network", "camera"), info.permissions)
            val unlisted = installerInfo(candidate(emptyMap()))
            assertEquals("", unlisted.description); assertEquals("", unlisted.licenseText)
        } finally { file.delete() }
    }
}
