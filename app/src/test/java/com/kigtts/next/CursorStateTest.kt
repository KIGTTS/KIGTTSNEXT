package com.kigtts.next

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.AppViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CursorStateTest {
    @Test fun relativeMovementPreservesGraphemesAndClampsToTextBounds() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val text = "A👨‍👩‍👧‍👦e\u0301Z"
        model.editText(TextFieldValue(text, TextRange(text.length)))
        model.moveCursor(-1); assertEquals(text.length - 1, model.state.value.editorValue.selection.start)
        model.moveCursor(-1); assertEquals(12, model.state.value.editorValue.selection.start)
        model.moveCursor(-1); assertEquals(1, model.state.value.editorValue.selection.start)
        model.moveCursor(-20); assertEquals(0, model.state.value.editorValue.selection.start)
        model.moveCursor(20); assertEquals(text.length, model.state.value.editorValue.selection.start)
    }
    @Test fun textFieldEditsPreserveImeCompositionAndCursorStripCommitsIt() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val value = TextFieldValue("你好ni", TextRange(4), TextRange(2, 4))
        model.editText(value)
        assertEquals(value, model.state.value.editorValue)
        model.moveCursor(-1)
        assertEquals(3, model.state.value.editorValue.selection.start)
        assertNull(model.state.value.editorValue.composition)
        assertEquals(value.text, model.state.value.editorText)
    }
}
