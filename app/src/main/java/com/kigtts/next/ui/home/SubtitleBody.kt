package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kigtts.next.state.*
import com.kigtts.next.ui.media.MediaTransition
import com.kigtts.next.text.formattedText
import androidx.compose.ui.text.input.*

@Composable
fun SubtitleBody(state: AppState, model: AppViewModel, modifier: Modifier = Modifier) {
    val media = model.mediaRepository.get(state.mediaId)
    MediaTransition(media,state,model,modifier.then(if(media!=null)Modifier.testTag("subtitle-content") else Modifier).clipToBounds(),
        renderFullCardMedia=false) {
        SubtitleText(state,model,Modifier.fillMaxSize())
    }
}

@Composable
private fun SubtitleText(state:AppState,model:AppViewModel,modifier:Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val flipped = state.subtitleFlipped && !state.editing
    val style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = preferredSubtitleFontSize(state.subtitleSizePercent),
        lineHeight = 1.25.em, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val density = LocalDensity.current
    val text = state.editorText.ifEmpty { "输入想说的话" }
    LaunchedEffect(state.editing) { if (state.editing) { focus.requestFocus(); keyboard?.show() } }
    BoxWithConstraints(modifier.testTag("subtitle-content").subtitleInputGestures(model, !state.editing).padding(20.dp, 12.dp)
        .semantics { stateDescription = if (flipped) "倒置字幕" else "正向字幕" }
        .subtitleZoom(model) { model.setSubtitleSizePercent(model.state.value.subtitleSizePercent * it) }
        .graphicsLayer { rotationZ = if (flipped) 180f else 0f }, contentAlignment = Alignment.Center) {
        val width = with(density) { maxWidth.roundToPx() }
        val height = with(density) { maxHeight.roundToPx() }
        if (state.editing) {
            val fitted = style.copy(fontSize = fittingSubtitleFontSize(formattedText(text, state.subtitleFormats), style, width, height).sp)
            CompositionLocalProvider(LocalSubtitleMagnifierEnabled provides false) {
            BasicTextField(state.editorValue, model::editText,
            modifier = Modifier.fillMaxWidth().testTag("speech-editor").focusRequester(focus).subtitleUpdateMotion(state.subtitleRevision),
            textStyle = fitted, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = VisualTransformation { TransformedText(formattedText(it.text, state.subtitleFormats), OffsetMapping.Identity) },
            decorationBox = { field ->
                if (state.editorText.isEmpty()) Text("输入想说的话", Modifier.fillMaxWidth(), style = fitted,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                field()
            })
            }
        } else SubtitleDisplay(state, model, text, style, width, height)
    }
}
