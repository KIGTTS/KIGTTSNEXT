package com.kigtts.next.ui.cards.camera

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun decodeCameraReferencePhoto(assets: AssetManager, sample: Int): Bitmap? =
    assets.open("camera/filter-reference.jpg").use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }

@Composable
internal fun rememberCameraReferencePhoto(load: Boolean): Bitmap? {
    val assets = LocalContext.current.assets
    return produceState<Bitmap?>(null, assets, load) {
        if (load && value == null) value = withContext(Dispatchers.IO) {
            runCatching { decodeCameraReferencePhoto(assets, 4) }.getOrNull()
        }
    }.value
}
