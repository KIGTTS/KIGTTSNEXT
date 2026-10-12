package com.kigtts.next

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.kigtts.next.ui.media.code.*
import org.junit.Test
import org.junit.Assert.*

class CodeEditorStateTest {
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
