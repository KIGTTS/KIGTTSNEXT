package com.kigtts.next.sharing

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable
internal fun FacePeerAvatar(avatar: String?) {
    val bitmap by produceState<ImageBitmap?>(null, avatar) {
        value = withContext(Dispatchers.Default) { runCatching {
            avatar?.takeIf { it.length < 65536 }?.let { text -> Base64.decode(text, Base64.DEFAULT) }?.let { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth in 1..512 && bounds.outHeight in 1..512) BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() else null
            }
        }.getOrNull() }
    }
    val shown = bitmap
    if (shown == null) Icon(Icons.Default.Person, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    else Image(shown, null, Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
}

@Composable
internal fun FaceReceivePrompt(offer: FaceOffer, approve: (String, Boolean) -> Unit, reject: (String) -> Unit) {
    var auto by remember(offer.id) { mutableStateOf(false) }
    MotionAlertDialog({ reject(offer.id) }, title = { Text("接收 ${offer.nickname} 的照片？") },
        icon = { FacePeerAvatar(offer.profile.optString("avatar").takeIf { it.isNotBlank() }) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (offer.profile.optString("bio").isNotBlank()) Text(offer.profile.optString("bio"))
                Text("${offer.assets.map { it.photo }.distinct().size} 张照片 · %.1f MB".format(offer.assets.sumOf { it.bytes } / 1048576.0))
                Row { Checkbox(auto, feedbackChange { auto = it }); Text("本次会话自动接收", Modifier.padding(top = 12.dp)) }
            }
        }, confirmButton = { Button(dialogFeedbackClick { approve(offer.id, auto) }) { Text("接收") } },
        dismissButton = { TextButton(dialogFeedbackClick { reject(offer.id) }) { Text("拒绝") } })
}
