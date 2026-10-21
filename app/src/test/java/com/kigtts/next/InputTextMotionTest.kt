package com.kigtts.next

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import com.kigtts.next.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class InputTextMotionTest {
    @Test fun composingTextAndSelectionAreImmediateWhileCommittedWordsCanFade() {
        val composing = TextFieldValue("你好", TextRange(2), TextRange(0, 2))
        assertNull(committedInsertion(TextFieldValue(""), composing))
        assertEquals(TextRange(0, 2), committedInsertion(composing, composing.copy(composition = null)))
        assertNull(committedInsertion(TextFieldValue("你好"), TextFieldValue("你好", TextRange(0))))
        assertNull(committedInsertion(TextFieldValue("你好"), TextFieldValue("")))
        assertEquals(TextRange(1, 2), committedInsertion(TextFieldValue("AB"), TextFieldValue("A字B")))
        assertEquals(TextRange(0, 2), committedInsertion(TextFieldValue("😀"), TextFieldValue("😁")))
    }
    @Test fun opacityPreservesTextOffsetsAndRichColorsAndLongInputStaysNative() {
        val text = buildAnnotatedString { append("前新字后"); addStyle(SpanStyle(color = Color.Red, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), 1, 3) }
        val result = fadedInsertion(text, TextRange(1, 3), .6f, Color.Black)
        assertEquals(text.text, result.text)
        assertEquals(Color.Red.copy(alpha = .6f), result.spanStyles.last().item.color)
        assertEquals(text.spanStyles.first(), result.spanStyles.first())
        assertSame(text, fadedInsertion(text, TextRange(1, 3), 1f, Color.Black))
        assertNull(committedInsertion(TextFieldValue(""), TextFieldValue("长".repeat(9000))))
    }
}
