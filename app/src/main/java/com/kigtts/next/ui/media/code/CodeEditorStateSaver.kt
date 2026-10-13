package com.kigtts.next.ui.media.code

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.kigtts.next.media.HtmlProjectFiles

/** Preserve the bounded draft and selection, rather than copying the entire undo history into a Bundle. */
internal fun codeEditorStateSaver(maxCharacters: Int = Int.MAX_VALUE, maxBytes: Int = HtmlProjectFiles.TEXT_LIMIT) =
    listSaver<CodeEditorState, Any>(
        save = { listOf(it.value.text, it.value.selection.start, it.value.selection.end) },
        restore = { saved ->
            val text = saved[0] as String
            CodeEditorState(text, maxCharacters = maxCharacters, maxBytes = maxBytes).also {
                it.change(TextFieldValue(text, TextRange((saved[1] as Int).coerceIn(0, text.length),
                    (saved[2] as Int).coerceIn(0, text.length))))
            }
        })
