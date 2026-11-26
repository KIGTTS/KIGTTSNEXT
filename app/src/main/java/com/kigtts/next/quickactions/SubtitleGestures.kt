package com.kigtts.next.quickactions

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties
import kotlin.math.sqrt

internal const val LEGACY_GESTURE_PLUGIN = "input.quick-gestures"

internal enum class SubtitleGesture(val id: String, val vertices: List<Offset>) {
    M("m_shape", listOf(Offset(0f, 1f), Offset(0f, 0f), Offset(.5f, .6f), Offset(1f, 0f), Offset(1f, 1f))),
    W("w_shape", listOf(Offset(0f, 0f), Offset(0f, 1f), Offset(.5f, .25f), Offset(1f, 1f), Offset(1f, 0f))),
    Z("z_shape", listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(0f, 1f), Offset(1f, 1f))),
    LIGHTNING("lightning", listOf(Offset(1f, 0f), Offset(.2f, .45f), Offset(.8f, .45f), Offset(0f, 1f))),
    HOURGLASS("hourglass", listOf(Offset(0f, 0f), Offset(1f, 1f), Offset(1f, 0f), Offset(0f, 1f))),
    HOOK("folded_hook", listOf(Offset(0f, 0f), Offset(0f, .82f), Offset(.25f, 1f), Offset(.75f, 1f),
        Offset(1f, .75f), Offset(1f, .38f), Offset(.72f, .2f)));

    val samples by lazy { sample(vertices) }
}

internal fun matchSubtitleGesture(points: List<Offset>, minimumSpan: Float): SubtitleGesture? {
    if (points.size !in 8..512 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
    val left = points.minOf { it.x }
    val top = points.minOf { it.y }
    val width = points.maxOf { it.x } - left
    val height = points.maxOf { it.y } - top
    if (minOf(width, height) < minimumSpan || minOf(width, height) < maxOf(width, height) * .2f) return null
    val trace = sample(points.map { Offset((it.x - left) / width, (it.y - top) / height) })
    val ranked = SubtitleGesture.entries.map { gesture ->
        val error = trace.indices.sumOf { (trace[it] - gesture.samples[it]).getDistanceSquared().toDouble() }
        gesture to sqrt(error / trace.size)
    }.sortedBy { it.second }
    val (best, error) = ranked.first()
    return best.takeIf { error <= .20 && ranked[1].second - error >= .035 }
}

private fun sample(points: List<Offset>): List<Offset> {
    val lengths = FloatArray(points.size)
    for (index in 1 until points.size) lengths[index] = lengths[index - 1] + (points[index] - points[index - 1]).getDistance()
    var end = 1
    return List(32) { index ->
        val distance = lengths.last() * index / 31
        while (end < lengths.lastIndex && lengths[end] < distance) end++
        val fraction = (distance - lengths[end - 1]) / (lengths[end] - lengths[end - 1]).coerceAtLeast(.0001f)
        points[end - 1] + (points[end] - points[end - 1]) * fraction
    }
}

internal data class SubtitleGestureBinding(val phraseId: String, val enabled: Boolean)
internal data class SubtitleGestureSettings(val enabled: Boolean, val bindings: Map<SubtitleGesture, SubtitleGestureBinding>) {
    val active = enabled && bindings.values.any { it.enabled }
}

internal class SubtitleGestureStore(context: Context) {
    private val prefs = context.getSharedPreferences("quick-actions", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val state = mutable.asStateFlow()

    fun reload() { mutable.value = load() }

    fun migrate(file: File) {
        if (prefs.getBoolean("gestures-migrated", false)) return
        val old = Properties()
        if (file.isFile && file.length() in 1..16384) {
            runCatching { file.inputStream().buffered().use(old::load) }
        }
        prefs.edit {
            putBoolean("gestures-migrated", true)
            if (!prefs.contains("gestures-enabled")) putBoolean("gestures-enabled", old.getProperty("enabled") == "true")
            SubtitleGesture.entries.forEach { gesture ->
                val entry = old.getProperty("${gesture.id}.entry").orEmpty().take(256)
                if (entry.isNotBlank() && !prefs.contains("gesture-${gesture.id}")) {
                    putString("gesture-${gesture.id}", entry)
                    putBoolean("gesture-enabled-${gesture.id}", old.getProperty("${gesture.id}.enabled") == "true")
                }
            }
        }
        reload()
    }

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean("gestures-enabled", value) }
        reload()
    }

    fun bind(gesture: SubtitleGesture, id: String) {
        prefs.edit {
            putString("gesture-${gesture.id}", id)
            putBoolean("gesture-enabled-${gesture.id}", true)
        }
        reload()
    }

    fun enable(gesture: SubtitleGesture, value: Boolean) {
        prefs.edit { putBoolean("gesture-enabled-${gesture.id}", value) }
        reload()
    }

    private fun load() = SubtitleGestureSettings(prefs.getBoolean("gestures-enabled", false),
        SubtitleGesture.entries.mapNotNull { gesture ->
            prefs.getString("gesture-${gesture.id}", null)?.takeIf(String::isNotBlank)?.let {
                gesture to SubtitleGestureBinding(it, prefs.getBoolean("gesture-enabled-${gesture.id}", true))
            }
        }.toMap())
}
