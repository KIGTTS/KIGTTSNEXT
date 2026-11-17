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
import com.kigtts.next.i18n.rememberUiCopy
import com.kigtts.next.i18n.uiCopy

internal fun shareSketchFile(context: Context, file: File) = shareSketchUri(context,
    FileProvider.getUriForFile(context, context.packageName + ".files", file))
internal fun shareSketchArtwork(context: Context, artwork: SketchArtwork) = shareSketchUri(context, Uri.parse(artwork.mediaUri))
private fun shareSketchUri(context: Context, uri: Uri) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri(context.uiCopy("sketch.artwork"), uri)
    }, context.uiCopy("sketch.share")))
}

/** Legacy Android needs write permission; modern Android only writes media owned by this app. */
@Composable internal fun rememberSketchSavePermission(save: () -> Unit, message: (String) -> Unit): () -> Unit {
    val copy = rememberUiCopy()
    val context = LocalContext.current; val current = rememberUpdatedState(save); val feedback = rememberUpdatedState(message)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) current.value() else feedback.value(copy("sketch.storage_permission"))
    }
    return {
        if (Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            current.value()
        else permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
