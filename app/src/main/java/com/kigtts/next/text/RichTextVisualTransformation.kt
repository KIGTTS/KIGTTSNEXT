package com.kigtts.next.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*

/** Selection, viewport and app-bar animation reuse spans instead of rebuilding the whole text. */
internal class RichTextVisualTransformation(private val formats: List<TextFormatRun>) : VisualTransformation {
    private var previous: AnnotatedString? = null
    private var transformed: TransformedText? = null
    override fun filter(text: AnnotatedString): TransformedText {
        if (text == previous) return requireNotNull(transformed)
        return TransformedText(formattedText(text.text, formats), OffsetMapping.Identity).also {
            previous = text; transformed = it
        }
    }
}
