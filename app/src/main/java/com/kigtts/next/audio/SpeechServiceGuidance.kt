package com.kigtts.next.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SpeechServiceStatus { MISSING, UNAVAILABLE, ENABLED }

/** Remember an explicit opt-out only while no synthesis service is installed. */
internal class SpeechServiceGuidance(context: Context, private val status: () -> SpeechServiceStatus,
    private val install: () -> Unit, private val manage: () -> Unit) {
    private val preferences = context.getSharedPreferences("speech-service-guidance", Context.MODE_PRIVATE)
    private val mutablePrompt = MutableStateFlow<SpeechServiceStatus?>(null)
    val prompt = mutablePrompt.asStateFlow()
    private val mutableVisible = MutableStateFlow(true)
    val visible = mutableVisible.asStateFlow()
    init { refresh() }

    fun refresh() {
        val current = status()
        if (current == SpeechServiceStatus.ENABLED) {
            preferences.edit().remove("hide-without-service").apply()
            mutablePrompt.value = null
        }
        mutableVisible.value = when (current) {
            SpeechServiceStatus.ENABLED -> true
            SpeechServiceStatus.UNAVAILABLE -> false
            SpeechServiceStatus.MISSING -> !preferences.getBoolean("hide-without-service", false)
        }
    }
    fun request(): Boolean {
        refresh()
        val current = status()
        if (current == SpeechServiceStatus.ENABLED) return true
        mutablePrompt.value = current
        return false
    }
    fun later() { mutablePrompt.value = null }
    fun hide() {
        preferences.edit().putBoolean("hide-without-service", true).apply()
        mutablePrompt.value = null
        refresh()
    }
    fun proceed() {
        val current = status()
        mutablePrompt.value = null
        if (current == SpeechServiceStatus.MISSING) install() else if (current == SpeechServiceStatus.UNAVAILABLE) manage()
    }
}
