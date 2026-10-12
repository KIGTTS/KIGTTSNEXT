package com.kigtts.next.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextDecoration

fun formattedText(text: String, formats: List<TextFormatRun>): AnnotatedString = buildAnnotatedString {
    append(text)
    TextFormatting.normalize(formats, text.length).forEach { run -> addStyle(when (run.kind) {
        TextFormat.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        TextFormat.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        TextFormat.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        TextFormat.COLOR -> SpanStyle(color = Color(run.value))
        TextFormat.SIZE -> SpanStyle(fontSize = (run.value / 100f).em)
    }, run.start, run.end) }
}
