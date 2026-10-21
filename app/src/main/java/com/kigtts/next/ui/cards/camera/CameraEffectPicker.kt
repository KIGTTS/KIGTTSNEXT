package com.kigtts.next.ui.cards.camera

import android.graphics.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.color.*
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun CameraEffectPicker(selected: PhotoEffect, effects: PhotoEffects, enabled: Boolean, vertical: Boolean,
    choose: (PhotoEffect) -> Unit) {
    val assets = LocalContext.current.assets
    val previews by produceState<Map<PhotoEffect, ImageBitmap>>(emptyMap(), assets) {
        value = withContext(Dispatchers.Default) {
            val source = assets.open("camera/filter-reference.jpg").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) }
            if (source == null) emptyMap() else try { PhotoEffect.entries.associateWith { effect ->
                val target = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                val side = minOf(source.width, source.height)
                Canvas(target).drawBitmap(source, Rect((source.width-side)/2,(source.height-side)/2,(source.width+side)/2,(source.height+side)/2), Rect(0,0,128,128), Paint(Paint.FILTER_BITMAP_FLAG))
                applyPhotoEffects(target, PhotoEffects.NONE.with(effect, .8f))
                target.asImageBitmap()
            } } finally { source.recycle() }
        }
    }
    val tile: @Composable (PhotoEffect) -> Unit = { effect ->
        val active = if (effect == PhotoEffect.NONE) !effects.active else effects.amount(effect) > 0f
        Surface(onClick = feedbackClick { choose(effect) }, enabled = enabled, modifier = Modifier.size(80.dp).testTag("camera-effect/"+effect.name)
            .semantics { this.selected = selected == effect }, shape = RoundedCornerShape(12.dp),
            border = if (selected == effect) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null) {
            Box(Modifier.fillMaxSize()) {
                previews[effect]?.let { Image(it, null, Modifier.fillMaxSize()) }
                Surface(Modifier.align(Alignment.BottomStart), color = Color.Black.copy(alpha = .68f)) {
                    Text((if (active && effect != PhotoEffect.NONE) "• " else "") + effect.label,
                        Modifier.padding(6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (vertical) LazyColumn(Modifier.width(80.dp).fillMaxHeight(), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PhotoEffect.entries, key = { it.name }) { tile(it) }
    } else LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(PhotoEffect.entries, key = { it.name }) { tile(it) } }
}
