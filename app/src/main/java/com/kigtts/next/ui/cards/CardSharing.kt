package com.kigtts.next.ui.cards

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Run on IO: later edits must not replace the file another application is reading. */
internal fun preparePolaroidShare(context: Context, source: File): File {
    val directory = File(context.cacheDir, "cards/exports").apply { check(mkdirs() || isDirectory) }
    return source.copyTo(File(directory, UUID.randomUUID().toString() + ".png"))
}

internal fun sharePolaroid(context: Context, image: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", image)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("拍立得", uri)
    }, "分享拍立得"))
}
