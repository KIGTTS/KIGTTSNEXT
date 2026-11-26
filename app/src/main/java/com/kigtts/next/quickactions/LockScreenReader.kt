package com.kigtts.next.quickactions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

internal class LockScreenReader(private val context: Context, private val unavailable: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var pending: Pair<String, String>? = null
    private var utterance = 0L
    var reading by mutableStateOf(false)
        private set

    fun toggle(text: String, locale: String) {
        if (reading) { stop(); return }
        if (closed || text.isBlank()) return
        pending = text to locale
        reading = true
        if (ready) {
            speakPending()
        } else if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { result ->
                main.post {
                    if (closed) return@post
                    if (result == TextToSpeech.SUCCESS) {
                        ready = true
                        tts?.setOnUtteranceProgressListener(listener)
                        speakPending()
                    } else {
                        tts?.shutdown()
                        tts = null
                        if (pending != null) fail()
                    }
                }
            }
        }
    }

    private fun speakPending() {
        val (text, locale) = pending ?: return
        pending = null
        val engine = tts ?: return fail()
        val language = Locale.forLanguageTag(locale)
        if (engine.isLanguageAvailable(language) >= TextToSpeech.LANG_AVAILABLE) engine.language = language
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, (++utterance).toString()) != TextToSpeech.SUCCESS) fail()
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) = Unit
        override fun onDone(id: String?) { main.post { if (id == utterance.toString()) reading = false } }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(id: String?) { main.post { if (id == utterance.toString()) fail() } }
    }

    private fun fail() { reading = false; pending = null; unavailable() }

    fun stop() {
        utterance++
        pending = null
        reading = false
        tts?.stop()
    }

    fun close() {
        closed = true
        stop()
        tts?.shutdown()
        tts = null
    }
}
