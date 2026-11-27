package com.kigtts.next.audio

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal enum class MissingPiperComponent(val label: String) {
    SERVICE("语音合成服务"), ENGINE("Piper 语音引擎")
}

/** The gate lives with the process; only an explicit checkbox persists across launches. */
internal class LegacyPiperGuidance(context: Context, private val gate: AtomicBoolean,
    private val hasPacks: () -> Boolean, private val missing: () -> Set<MissingPiperComponent>,
    private val install: (String) -> Unit) {
    private val preferences = context.getSharedPreferences("legacy-piper-guidance", Context.MODE_PRIVATE)
    private val mutablePrompt = MutableStateFlow<Set<MissingPiperComponent>?>(null)
    val prompt = mutablePrompt.asStateFlow()
    suspend fun onStartup() {
        if (!gate.compareAndSet(false, true) || preferences.getBoolean("never-remind", false)) return
        if (withContext(Dispatchers.IO) { runCatching(hasPacks).getOrDefault(false) }) onDetected()
    }
    fun onDetected() {
        if (!preferences.getBoolean("never-remind", false))
            mutablePrompt.value = missing().takeIf { it.isNotEmpty() }
    }
    fun dismiss(neverRemind: Boolean) {
        if (neverRemind) preferences.edit().putBoolean("never-remind", true).apply()
        mutablePrompt.value = null
    }
    fun proceed(neverRemind: Boolean) {
        if (mutablePrompt.value == null) return
        val components = missing()
        dismiss(neverRemind)
        if (components.isNotEmpty()) install(if (MissingPiperComponent.ENGINE in components) "Piper" else "语音合成服务")
    }
}
