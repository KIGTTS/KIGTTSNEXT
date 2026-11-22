package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import com.kigtts.next.state.AppViewModel

/** Keep native semantics/scrolling while reusing the editor's geometry-only reflow. */
@Composable
internal fun SubtitleDisplayParagraph(text: AnnotatedString, style: TextStyle, width: Int, height: Int,
    alignment: Alignment, animateReflow: Boolean, model: AppViewModel, tint: ColorProducer) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val motion = rememberSubtitleGlyphMotion(layout?.takeIf { it.layoutInput.text == text }, width, height, alignment, null, animateReflow,
        SubtitleGlyphPolicy(layoutChangesOnly = true))
    val drawn = motion.frame
    val measuredCurrentGeometry = drawn?.let { it.viewport.width == width && it.viewport.height == height &&
        it.layout.layoutInput.style.fontSize == style.fontSize } == true
    val custom = animateReflow && drawn != null && drawn.layout.layoutInput.text == text &&
        (!measuredCurrentGeometry || drawn.layout.size.height <= height) && drawn.glyphs.size <= 240
    Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
        BasicText(text, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("speech-editor")
            .subtitleInputGestures(model).drawWithContent { if (!custom) drawContent() },
            style = style, onTextLayout = { layout = it }, color = tint)
        SubtitleGlyphCanvas(motion, custom, Modifier.matchParentSize().clearAndSetSemantics { }, tint)
    }
}
