package com.kigtts.next.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Stable export copies survive closing the temporary face-to-face session. Call on IO. */
internal fun faceSystemShareIntent(context: Context, assets: List<FaceAsset>, personal: Boolean): Intent {
    val photos = assets.filterNot { it.format == "PROFILE" }
    require(photos.isNotEmpty()) { "没有可以分享的照片" }
    val root = File(context.cacheDir, "cards/exports").apply { check(mkdirs() || isDirectory) }
    val uris = photos.map { asset ->
        val source = requireNotNull(asset.file)
        val copy = source.copyTo(File(root, "${UUID.randomUUID()}.${FacePhotoSession.extension(asset.mime)}"))
        FileProvider.getUriForFile(context, context.packageName + ".files", copy)
    }
    val send = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = photos.map { it.mime }.distinct().singleOrNull() ?: "image/*"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData.newRawUri(if (personal) "名片" else "照片", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, if (personal) "分享名片" else "分享照片")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
