package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.PolaroidFormat

/** The tab's paper and aperture use the same physical proportions as the finished photograph. */
@Composable internal fun CaptureFormatTab(format: PolaroidFormat, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    val copy = rememberCameraCopy()
    val geometry = format.geometry
    val colors = MaterialTheme.colorScheme
    Surface(onClick = feedbackClick(choose), enabled = enabled,
        modifier = Modifier.width(100.dp).testTag("capture-format/" + format.name).semantics {
            this.selected = selected; role = Role.Tab
            contentDescription = when (format) { PolaroidFormat.MINI -> copy.text("camera.paper_mini"); PolaroidFormat.WIDE -> copy.text("camera.paper_wide"); else -> copy.text("camera.paper_square") }
        }, shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.secondaryContainer else Color.Transparent) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size((64 * geometry.aspectRatio).dp, 64.dp).testTag("capture-format-paper/" + format.name)) {
                val scale = size.height / geometry.paperHeight.toFloat()
                drawRoundRect(Color.White, cornerRadius = CornerRadius(2.dp.toPx()))
                drawRoundRect(if (selected) colors.primary else colors.outlineVariant,
                    cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
                drawRect(if (selected) colors.primary.copy(alpha = .75f) else colors.onSurfaceVariant.copy(alpha = .28f),
                    Offset(geometry.photoLeft.toFloat() * scale, geometry.photoTop.toFloat() * scale),
                    Size(geometry.photoWidth.toFloat() * scale, geometry.photoHeight.toFloat() * scale))
            }
            Text(when (format) { PolaroidFormat.MINI -> "Mini"; PolaroidFormat.WIDE -> "Wide"; else -> "Square" },
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
