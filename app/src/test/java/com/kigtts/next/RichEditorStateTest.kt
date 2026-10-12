package com.kigtts.next

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import com.kigtts.next.text.*
import org.junit.Test
import org.junit.Assert.*

class RichEditorStateTest {
    @Test fun textAndFormatUndoTogetherButMovingTheSelectionDoesNotAddHistory() {
        val editor=RichEditorState("hello",emptyList())
        editor.change(TextFieldValue("hello",TextRange(0,5)))
        assertFalse(editor.canUndo)
        editor.format(TextRange(0,5),TextFormat.UNDERLINE)
        val formatted=editor.formats
        editor.change(TextFieldValue("hello!",TextRange(6)))
        editor.undo();assertEquals("hello",editor.value.text);assertEquals(formatted,editor.formats)
        editor.undo();assertTrue(editor.formats.isEmpty())
        editor.redo();assertEquals(formatted,editor.formats)
        editor.redo();assertEquals("hello!",editor.value.text)
        editor.change(TextFieldValue("new"));assertFalse(editor.canRedo)
    }
    @Test fun transformationRetainsTheSameSpanResultForUnchangedTextDuringLayoutAndSelectionChanges() {
        val transformation=RichTextVisualTransformation(listOf(TextFormatRun(0,5,TextFormat.BOLD)))
        val first=transformation.filter(AnnotatedString("hello"))
        repeat(60) { assertSame(first,transformation.filter(AnnotatedString("hello"))) }
        assertNotSame(first,transformation.filter(AnnotatedString("hello!")))
    }
}
