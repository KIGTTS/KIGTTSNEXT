@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.media

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.kigtts.next.media.MediaScale
import kotlinx.coroutines.delay
import java.io.File

/** One player/output surface. Only a small frame is read for the background; no second decoder. */
@Composable
fun rememberVideoBackdrop(file: File, view: PlayerView?, player: ExoPlayer, active: Boolean, scale: MediaScale, rendered: Boolean): Bitmap? {
    var frame by remember(file.path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(view, player, active, scale, rendered) {
        if (!active || scale != MediaScale.FIT || !rendered) return@LaunchedEffect
        var previous = Long.MIN_VALUE
        while (true) {
            val texture = view?.videoSurfaceView as? TextureView
            val position = player.currentPosition
            val bars = view != null && texture != null && view.width > 0 && view.height > 0 &&
                kotlin.math.abs(texture.width.toFloat() / view.width - texture.height.toFloat() / view.height) > .02f
            if (bars && texture?.isAvailable == true && texture.width > 0 && texture.height > 0 && (position != previous || frame == null)) {
                videoFrameSnapshot(texture)?.let { frame = it; previous = position }
            }
            delay(250)
        }
    }
    return frame
}
