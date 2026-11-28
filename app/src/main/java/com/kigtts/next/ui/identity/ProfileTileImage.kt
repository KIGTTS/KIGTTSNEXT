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
@Composable internal fun ProfileTileImage(file: File?, modifier: Modifier = Modifier, description: String? = null,
    fit: Boolean = false) {
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val drawable by produceState<Drawable?>(null, file?.path, file?.lastModified()) {
        value = withContext(Dispatchers.IO) { file?.takeIf(File::isFile)?.let { decodeProfileImage(view.resources, it) } }
    }
    if (drawable == null) Icon(Icons.Default.Image, description, modifier)
    else {
        val visibility = if (drawable is Animatable) Modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            visible = bounds.width > 0f && bounds.height > 0f
        } else Modifier
        AndroidView(factory = { ImageView(it) }, modifier = modifier.then(visibility),
            update = { view -> view.scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                if (view.drawable !== drawable) view.setImageDrawable(drawable); view.contentDescription = description })
        DisposableEffect(drawable, visible, lifecycle) {
            if (visible && lifecycle.isAtLeast(Lifecycle.State.STARTED)) (drawable as? Animatable)?.start() else (drawable as? Animatable)?.stop()
            onDispose { (drawable as? Animatable)?.stop() }
        }
    }
}
