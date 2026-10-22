package com.kigtts.next

import com.kigtts.next.ui.plugins.*
import com.kigtts.plugin.runtime.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.*

class PackageInstallerInfoTest {
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
