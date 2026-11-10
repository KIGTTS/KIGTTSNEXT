@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.PolaroidFrames
import com.kigtts.next.cards.PhotoFrameSpec
import com.kigtts.next.ui.components.feedbackClick

private val watermarkChoices = listOf("classic" to "浅色", "night" to "深色", "slim" to "细边")

internal fun cameraWatermarkLabel(style: String): String =
    watermarkChoices.firstOrNull { it.first == style }?.second ?: "关闭"

@Composable
internal fun CameraWatermarkPage(options: CameraOptions, snapshot: Bitmap?, enabled: Boolean, back: () -> Unit) {
    val showing = options.watermark != "none"
    val selected = options.watermarkStyle.takeIf { style -> watermarkChoices.any { it.first == style } } ?: "classic"
    val preview = remember(snapshot) { snapshot?.asImageBitmap() }
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            IconButton(feedbackClick(back), Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回相机设置")
            }
            Text("边框水印", style = MaterialTheme.typography.titleLarge)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            listOf("关闭", "开启").forEachIndexed { i, label ->
                SegmentedButton(selected = showing == (i == 1), enabled = enabled,
                    onClick = feedbackClick { options.chooseWatermark(if (i == 1) selected else "none") },
                    shape = SegmentedButtonDefaults.itemShape(i, 2), label = { Text(label) })
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(watermarkChoices, key = { it.first }) { (id, label) ->
                val current = id == selected
                val style = PolaroidFrames.defaults.first { it.id == id }
                val paper = Color(style.color ?: 0xFFFAFAFA)
                Column(Modifier.width(124.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (current && showing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .selectable(selected = current && showing, enabled = enabled, role = Role.RadioButton,
                        onClick = feedbackClick { options.chooseWatermark(id) }).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                        val frame = PhotoFrameSpec(preview?.width ?: 4, preview?.height ?: 3, id).geometry()
                        val size = cameraPhotoWindow(maxWidth.value, maxHeight.value, (frame.paperWidth / frame.paperHeight).toFloat())
                        val unit = size.width / frame.paperWidth
                        Box(Modifier.size(size.width.dp, size.height.dp).background(paper)) {
                            val window = Modifier.offset((frame.photoLeft * unit).dp, (frame.photoTop * unit).dp)
                                .size(((frame.photoRight - frame.photoLeft) * unit).dp, ((frame.photoBottom - frame.photoTop) * unit).dp)
                            if (preview != null) Image(preview, null, window, contentScale = ContentScale.Fit)
                            else Box(window.background(Brush.linearGradient(listOf(
                                MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.primary))))
                        }
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
