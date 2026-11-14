package com.kigtts.next.ui.identity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kigtts.next.identity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

@Composable
internal fun ProfileQrTile(value: String, style: ProfileQrStyle, label: String, modifier: Modifier = Modifier) {
    val matrix by key(value) {
        produceState<com.google.zxing.common.BitMatrix?>(null) {
            this.value = withContext(Dispatchers.Default) { profileQrMatrix(value) }
        }
    }
    val colors = MaterialTheme.colorScheme
    // Keep a high-contrast light quiet zone even when the app theme is dark.
    val foreground = if (style == ProfileQrStyle.INK) Color.Black else lerp(colors.primary, Color.Black, .62f)
    val paper = if (style == ProfileQrStyle.INK) Color.White else lerp(colors.primaryContainer, Color.White, .90f)
    Box(modifier, contentAlignment = Alignment.Center) {
        val code = matrix
        if (code != null) Canvas(Modifier.fillMaxSize().semantics { contentDescription = label.ifBlank { "二维码" } }) {
            val step = floor(size.minDimension / code.width)
            if (step < 1f) return@Canvas
            val side = step * code.width
            val origin = Offset(floor((size.width - side) / 2f), floor((size.height - side) / 2f))
            drawRect(paper, origin, Size(side, side))
            for (y in 0 until code.height) for (x in 0 until code.width) if (code[x, y]) {
                val start = origin + Offset(x * step, y * step)
                if (style == ProfileQrStyle.DOTS && step >= 3f && code.qrDotAllowed(x, y))
                    drawCircle(foreground, step * .48f, start + Offset(step / 2, step / 2))
                else drawRect(foreground, start, Size(step, step))
            }
        } else if (value.isBlank()) Text("二维码", style = MaterialTheme.typography.labelMedium)
    }
}
