package com.kigtts.next.text

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Text and formatting travel through undo/redo together; selection alone creates no undo entry. */
@Stable
internal class RichEditorState(text: String, initialFormats: List<TextFormatRun>, selection: Int = 0) {
    private data class Snapshot(val value: TextFieldValue, val formats: List<TextFormatRun>)
    var value by mutableStateOf(TextFieldValue(text, TextRange(selection.coerceIn(0, text.length)))); private set
    var formats by mutableStateOf(TextFormatting.normalize(initialFormats, text.length)); private set
    private val undo = mutableStateListOf<Snapshot>()
    private val redo = mutableStateListOf<Snapshot>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    private fun snapshot() = Snapshot(value.copy(composition = null), formats)
    fun change(next: TextFieldValue) {
        require(next.text.toByteArray().size <= 128 * 1024) { "内容太长，请缩短后再保存" }
        commit(next, TextFormatting.rebase(formats, value.text, next.text))
    }
    fun format(range: TextRange, kind: TextFormat, argument: Long = 0) =
        commit(value.copy(composition = null), TextFormatting.apply(formats, value.text.length, range.start, range.end, kind, argument))
    private fun commit(next: TextFieldValue, runs: List<TextFormatRun>) {
        if (next.text != value.text || runs != formats) {
            undo.add(snapshot()); redo.clear()
            while (undo.size > 20 || undo.sumOf { it.value.text.length.toLong() } > 4L * 1024 * 1024) undo.removeAt(0)
        }
        value = next; formats = runs
    }
    fun undo() { if (canUndo) { redo.add(snapshot()); restore(undo.removeAt(undo.lastIndex)) } }
    fun redo() { if (canRedo) { undo.add(snapshot()); restore(redo.removeAt(redo.lastIndex)) } }
    private fun restore(snapshot: Snapshot) { value = snapshot.value; formats = snapshot.formats }
}
