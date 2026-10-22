package com.kigtts.next

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kigtts.next.ui.plugins.*
import com.kigtts.plugin.runtime.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PackageInstallerArtworkTest {
    @get:Rule val compose = createComposeRule()
    @Test fun declaredSvgAndBitmapAreShownByInstallerAndUnsafeArtworkFallsBack() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" fill="#038387"/></svg>""".toByteArray()
        val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        for ((extension, bytes) in listOf("svg" to svg, "png" to png)) {
            val file = Files.createTempFile("installer-art", ".zip").toFile()
            try {
                val path = "assets/icon.$extension"
                ZipOutputStream(file.outputStream()).use { zip -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
                val manifest = PackageManifest("test.art", "测试插件", "1.0", 1, "test.Plugin", files = mapOf(path to "signed"), icon = PackageIcon(path = path))
                val info = installerInfo(PendingPackage(file, PackagePreview(manifest, "a".repeat(64))))
                assertNotNull(info.image); assertTrue(info.image!!.width <= 256)
                if (extension == "svg") {
                    compose.setContent { MaterialTheme { PackageInstallerDialog(listOf(info), false, false, null, install = {}, dismiss = {}) } }
                    compose.onNodeWithTag("installer-package-image").assertIsDisplayed()
                    compose.onNodeWithText("测试插件").assertIsDisplayed()
                    compose.onNodeWithText("安装插件").assertIsDisplayed()
                }
            } finally { file.delete() }
        }
        assertNull(packageIconBitmap("bad.svg", """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""".toByteArray()))
        assertNull(packageIconBitmap("remote.svg", """<svg xmlns="http://www.w3.org/2000/svg"><image href="https://example.invalid/icon.png"/></svg>""".toByteArray()))
        assertNull(packageIconBitmap("bad.png", "invalid".toByteArray()))
    }
}
