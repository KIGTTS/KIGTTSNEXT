package com.kigtts.next.ui.media.code

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CodeTextArea(state:CodeEditorState,enabled:Boolean,focus:FocusRequester,modifier:Modifier,error:(String)->Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val vertical=rememberScrollState();val horizontal=rememberScrollState()
    val density=androidx.compose.ui.platform.LocalDensity.current
    val foreground=MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val currentLine=state.value.text.take(state.value.selection.end).count { it=='\n' }
    BoxWithConstraints(modifier) {
        val viewport=maxHeight;val width=(maxWidth-48.dp).coerceAtLeast(1.dp)
        Row(Modifier.fillMaxSize().verticalScroll(vertical)) {
            Canvas(Modifier.width(48.dp).height(with(density) { (layout?.size?.height ?: 0).toDp()+16.dp }.coerceAtLeast(viewport)).testTag("code-line-numbers")) {
                val result=layout ?: return@Canvas
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=foreground;textSize=with(density) { 12.sp.toPx() };textAlign=Paint.Align.RIGHT }
                val first=result.getLineForVerticalPosition(vertical.value.toFloat()).coerceAtLeast(0)
                val last=result.getLineForVerticalPosition((vertical.value+with(density){viewport.toPx()}).coerceAtMost(result.size.height.toFloat()))
                for(line in first..last) {
                    paint.alpha=if(line==currentLine)255 else 150
                    drawContext.canvas.nativeCanvas.drawText((line+1).toString(),size.width-8.dp.toPx(),result.getLineBaseline(line)+8.dp.toPx(),paint)
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(horizontal)) {
                BasicTextField(state.value,{ next->try { state.change(next) } catch(e:IllegalArgumentException) { error(e.message ?: "内容过长") } },
                    Modifier.widthIn(min=width).padding(8.dp).focusRequester(focus).testTag("content-editor-text"),enabled=enabled,
                    textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurface),
                    cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),onTextLayout={layout=it})
            }
        }
    }
}
