package com.kigtts.next.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.core.content.FileProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [FaceWindowsFileProvider::class])
class FaceSystemShareTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun asset(root: File, name: String, mime: String = "image/png", format: String = "SQUARE"): FaceAsset {
        val file = root.resolve(name).apply { writeText("photo $name") }
        return FaceAsset(faceToken(), faceToken(), if (format == "PROFILE") "profile" else "polaroid", name, mime,
            file.length(), sha256(file), format, file)
    }
    @Suppress("DEPRECATION")
    @Test fun singlePhotoUsesSystemChooserAndStableExportThatSurvivesEndingTheSession() {
        val root = Files.createTempDirectory("face-system-share").toFile()
        try {
            val file = asset(root, "one.png")
            val chooser = faceSystemShareIntent(context, listOf(file), false)
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val share = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND, share.action); assertEquals("image/png", share.type)
            val uri = requireNotNull(share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("content", uri.scheme); assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            root.deleteRecursively()
            val exported = File(context.cacheDir, "cards/exports/${uri.lastPathSegment}")
            assertTrue(exported.isFile); assertEquals("photo one.png", exported.readText())
            exported.delete()
        } finally { root.deleteRecursively() }
    }
    @Suppress("DEPRECATION")
    @Test fun batchSharingUsesMultipleImagesAndDoesNotIncludePrivateProfileTileAttachments() {
        val root = Files.createTempDirectory("face-system-multi").toFile()
        try {
            val chooser = faceSystemShareIntent(context, listOf(asset(root, "one.png"), asset(root, "two.jpg", "image/jpeg"),
                asset(root, "tile.gif", "image/gif", "PROFILE")), false)
            val share = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND_MULTIPLE, share.action); assertEquals("image/*", share.type)
            assertEquals(2, share.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.size)
            assertEquals(2, share.clipData?.itemCount)
            assertEquals("分享照片", chooser.getStringExtra(Intent.EXTRA_TITLE))
        } finally { root.deleteRecursively() }
    }
}

/** Android's provider hardcodes POSIX separators. Use the same configured roots on Windows. */
@Implements(FileProvider::class)
class FaceWindowsFileProvider {
    companion object {
        @JvmStatic @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            ReflectionHelpers.getStaticField<MutableMap<String, Any>>(FileProvider::class.java, "sCache").clear()
            val strategy = ReflectionHelpers.callStaticMethod<Any>(FileProvider::class.java, "getPathStrategy",
                ClassParameter.from(Context::class.java, context), ClassParameter.from(String::class.java, authority),
                ClassParameter.from(Int::class.javaPrimitiveType!!, 0))
            val roots = ReflectionHelpers.getField<Map<String, File>>(strategy, "mRoots")
            val path = file.canonicalFile.toPath()
            val root = requireNotNull(roots.entries.filter { path.startsWith(it.value.canonicalFile.toPath()) }
                .maxByOrNull { it.value.canonicalPath.length }) { "File is outside configured sharing roots" }
            return Uri.Builder().scheme("content").authority(authority).appendPath(root.key).apply {
                root.value.canonicalFile.toPath().relativize(path).forEach { appendPath(it.toString()) }
            }.build()
        }
    }
}
