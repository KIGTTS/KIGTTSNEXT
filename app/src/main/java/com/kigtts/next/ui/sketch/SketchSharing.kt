package com.kigtts.next.ui.sketch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

internal fun shareSketchFile(context: Context, file: File) = shareSketchUri(context,
    FileProvider.getUriForFile(context, context.packageName + ".files", file))
internal fun shareSketchArtwork(context: Context, artwork: SketchArtwork) = shareSketchUri(context, Uri.parse(artwork.mediaUri))
private fun shareSketchUri(context: Context, uri: Uri) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("画板作品", uri)
    }, "分享画板"))
}

/** Legacy Android needs write permission; modern Android only writes media owned by this app. */
@Composable internal fun rememberSketchSavePermission(save: () -> Unit, message: (String) -> Unit): () -> Unit {
    val context = LocalContext.current; val current = rememberUpdatedState(save); val feedback = rememberUpdatedState(message)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) current.value() else feedback.value("需要存储权限才能保存到系统相册")
    }
    return {
        if (Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            current.value()
        else permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
