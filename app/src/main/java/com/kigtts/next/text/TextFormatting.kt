package com.kigtts.next.text

enum class TextFormat { BOLD, ITALIC, COLOR, SIZE }
data class TextFormatRun(val start: Int, val end: Int, val kind: TextFormat, val value: Long = 0)

object TextFormatting {
    fun normalize(runs: List<TextFormatRun>, length: Int): List<TextFormatRun> = runs.take(256).mapNotNull {
        val start = it.start.coerceIn(0, length); val end = it.end.coerceIn(start, length)
        if (start == end) null else it.copy(start = start, end = end, value = when (it.kind) {
            TextFormat.SIZE -> it.value.coerceIn(75, 400)
            TextFormat.COLOR -> 0xFF000000L or (it.value and 0xFFFFFF)
            else -> 0
        })
    }
    fun apply(runs: List<TextFormatRun>, length: Int, start: Int, end: Int, kind: TextFormat, value: Long = 0): List<TextFormatRun> {
        val from = minOf(start, end).coerceIn(0, length); val to = maxOf(start, end).coerceIn(from, length)
        if (from == to) return runs
        val normalized = normalize(runs, length)
        val toggleOff = kind in setOf(TextFormat.BOLD, TextFormat.ITALIC) &&
            normalized.any { it.kind == kind && it.start <= from && it.end >= to }
        val remaining = normalized.flatMap { run ->
            if (run.kind != kind || run.end <= from || run.start >= to) listOf(run) else listOfNotNull(
                run.copy(end = from).takeIf { it.start < it.end }, run.copy(start = to).takeIf { it.start < it.end })
        }
        return normalize(if (toggleOff || kind == TextFormat.SIZE && value == 100L) remaining
            else remaining + TextFormatRun(from, to, kind, value), length)
    }
    /** Keep formatting attached to the surviving text through insertion, deletion and IME replacement. */
    fun rebase(runs: List<TextFormatRun>, before: String, after: String): List<TextFormatRun> {
        if (before == after) return normalize(runs, after.length)
        var from = 0
        while (from < minOf(before.length, after.length) && before[from] == after[from]) from++
        var tail = 0
        while (tail < minOf(before.length, after.length) - from && before[before.lastIndex - tail] == after[after.lastIndex - tail]) tail++
        val end = before.length - tail; val delta = after.length - before.length
        return normalize(runs.mapNotNull { run -> when {
            run.end <= from -> run
            run.start >= end -> run.copy(start = run.start + delta, end = run.end + delta)
            run.start >= from && run.end <= end -> null
            else -> run.copy(start = minOf(run.start, from), end = if (run.end >= end) run.end + delta else from)
        } }, after.length)
    }
}
