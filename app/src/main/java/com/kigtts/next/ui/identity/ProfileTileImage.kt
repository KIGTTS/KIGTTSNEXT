package com.kigtts.next.ui.identity

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Bounded image decoding with composition-owned GIF playback, including Android 8. */
@Composable internal fun ProfileTileImage(file: File?, modifier: Modifier = Modifier, description: String? = null) {
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val drawable by produceState<Drawable?>(null, file?.path, file?.lastModified()) {
        value = withContext(Dispatchers.IO) { file?.takeIf(File::isFile)?.let { decodeProfileImage(view.resources, it) } }
    }
    if (drawable == null) Icon(Icons.Default.Image, description, modifier)
    else {
        AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, modifier = modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow(); visible = bounds.bottom > 0 && bounds.top < view.rootView.height && bounds.right > 0 && bounds.left < view.rootView.width
        },
            update = { view -> if (view.drawable !== drawable) view.setImageDrawable(drawable); view.contentDescription = description })
        DisposableEffect(drawable, visible, lifecycle) {
            if (visible && lifecycle.isAtLeast(Lifecycle.State.STARTED)) (drawable as? Animatable)?.start() else (drawable as? Animatable)?.stop()
            onDispose { (drawable as? Animatable)?.stop() }
        }
    }
}
