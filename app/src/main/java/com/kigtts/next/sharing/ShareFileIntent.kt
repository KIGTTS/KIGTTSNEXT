package com.kigtts.next.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Give other apps a stable, narrowly scoped copy; never expose plugin storage. Call on IO. */
internal fun shareFileIntent(context: Context, source: File, name: String, mime: String): Intent {
    val directory = File(context.cacheDir, "cards/exports/${UUID.randomUUID()}").apply { mkdirs() }
    val safe = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(120).ifBlank { "KIGTTS" }
    val copy = source.copyTo(File(directory, safe))
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", copy)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime; putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(safe, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
