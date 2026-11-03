package com.kigtts.next.cards

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Every optional disclosure has a native confirmation, independent of Agent access mode. */
internal object PhotoFactsConsent {
    data class Request(val kind: String, val result: CompletableDeferred<Boolean> = CompletableDeferred(), val service: String = "")
    val pending = MutableStateFlow<Request?>(null)
    private val mutex = Mutex()
    fun rememberedFields(context: Context): Set<PhotoTextField> {
        val prefs = context.getSharedPreferences("polaroid-facts", 0)
        if (!prefs.getBoolean("location", false)) return emptySet()
        return buildSet {
            add(PhotoTextField.LOCATION)
            if (prefs.getBoolean("weather", false)) add(PhotoTextField.WEATHER)
        }
    }
    suspend fun request(context: Context, kind: String,
        settings: PhotoServiceConfig = PhotoServiceSettings(context).snapshot()): Boolean = mutex.withLock {
        val prefs = context.getSharedPreferences("polaroid-facts", 0)
        val key = when {
            kind == "weather" && settings.weatherSource != PhotoWeatherSource.OPEN_METEO -> "weather-${settings.weatherSource.name}"
            kind == "location" && settings.placeSource == PhotoPlaceSource.AMAP -> "location-AMAP"
            else -> kind
        }
        if (prefs.getBoolean(key, false)) return@withLock true
        val service = if (kind == "weather") settings.weatherSource.label else settings.placeSource.label
        val request = Request(kind, service = service); pending.value = request
        try {
            val allowed = withTimeoutOrNull(90000) { request.result.await() } == true
            if (allowed) prefs.edit().putBoolean(key, true).apply()
            allowed
        } finally { if (pending.value === request) pending.value = null }
    }
}
