package com.kigtts.next

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.kigtts.next.ui.media.code.*
import org.junit.Test
import org.junit.Assert.*

class CodeEditorStateTest {
    @Test fun pluginLimitsRejectChangesBeforeLosingTextOrUndoHistory() {
        val state = CodeEditorState("abc", maxCharacters = 4, maxBytes = 6)
        state.change(TextFieldValue("abcd", TextRange(4)))
        assertThrows(IllegalArgumentException::class.java) { state.invoke(CodeKey.TAB) }
        assertEquals("abcd", state.value.text)
        assertThrows(IllegalArgumentException::class.java) { state.change(TextFieldValue("一二三")) }
        assertEquals("abcd", state.value.text)
        state.invoke(CodeKey.UNDO)
        assertEquals("abc", state.value.text)
        assertEquals("a".repeat(65_000), CodeEditorState("a".repeat(65_000)).value.text)
    }
    @Test fun unicodeCursorCommandsAndLogicalLinesNeverSplitSurrogates() {
        val state=CodeEditorState("a😀b\n12\n一二三四",3)
        state.invoke(CodeKey.LEFT);assertEquals(1,state.value.selection.end)
        state.invoke(CodeKey.RIGHT);assertEquals(3,state.value.selection.end)
        state.invoke(CodeKey.DOWN);assertEquals(7,state.value.selection.end)
        state.invoke(CodeKey.DOWN);assertEquals(10,state.value.selection.end)
        state.invoke(CodeKey.HOME);assertEquals(8,state.value.selection.end)
        state.invoke(CodeKey.END);assertEquals(12,state.value.selection.end)
        state.invoke(CodeKey.UP);assertEquals(7,state.value.selection.end)
    }
    @Test fun insertionReplacesSelectionAndUndoRedoRestoreSelectionAndText() {
        val state=CodeEditorState("abcd")
        state.change(TextFieldValue("abcd",TextRange(1,3)));state.invoke(CodeKey.LEFT_BRACE)
        assertEquals("a{d",state.value.text);assertEquals(2,state.value.selection.end)
        state.invoke(CodeKey.UNDO);assertEquals("abcd",state.value.text);assertEquals(TextRange(1,3),state.value.selection)
        state.invoke(CodeKey.REDO);assertEquals("a{d",state.value.text)
        state.invoke(CodeKey.TAB);assertEquals("a{\td",state.value.text)
        state.invoke(CodeKey.UNDO);state.invoke(CodeKey.SEMICOLON);assertFalse(state.available(CodeKey.REDO))
    }
}
