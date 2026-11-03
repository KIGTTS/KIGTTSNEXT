package com.kigtts.next.cards

import android.content.Context

internal enum class PhotoPlaceSource(val label: String) {
    SYSTEM("系统优先"), OFFLINE("离线地名"), AMAP("高德地图")
}

internal enum class PhotoWeatherSource(val label: String, val description: String) {
    OPEN_METEO("Open-Meteo", "免密钥 · 支持历史天气"),
    AMAP("高德天气", "中国地区实况 · 需要 Web 服务 Key"),
    SENIVERSE("心知天气", "城市天气实况 · 需要 API Key")
}

internal data class PhotoManualLocation(val name: String, val latitude: Double, val longitude: Double)

internal class PhotoServiceConfig(val placeSource: PhotoPlaceSource, val weatherSource: PhotoWeatherSource,
    val amapKey: String = "", val seniverseKey: String = "") {
    fun key(source: PhotoWeatherSource) = when (source) {
        PhotoWeatherSource.AMAP -> amapKey
        PhotoWeatherSource.SENIVERSE -> seniverseKey
        PhotoWeatherSource.OPEN_METEO -> ""
    }
}

/** Preferences are private to the app; credentials never become photo metadata or share payloads. */
internal class PhotoServiceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("polaroid-facts", Context.MODE_PRIVATE)
    var recordCapture: Boolean
        get() = prefs.getBoolean("record-capture-info", prefs.getBoolean("location", false) || prefs.getBoolean("weather", false))
        set(value) { prefs.edit().putBoolean("record-capture-info", value).apply() }
    var placeSource: PhotoPlaceSource
        get() = PhotoPlaceSource.entries.find { it.name == prefs.getString("place-source", null) } ?: PhotoPlaceSource.SYSTEM
        set(value) { prefs.edit().putString("place-source", value.name).apply() }
    var weatherSource: PhotoWeatherSource
        get() = PhotoWeatherSource.entries.find { it.name == prefs.getString("weather-source", null) } ?: PhotoWeatherSource.OPEN_METEO
        set(value) { prefs.edit().putString("weather-source", value.name).apply() }
    var manual: Boolean
        get() = prefs.getBoolean("manual-location", false)
        set(value) { prefs.edit().putBoolean("manual-location", value).apply() }
    val manualLocation: PhotoManualLocation?
        get() {
            val lat = prefs.getString("manual-latitude", null)?.toDoubleOrNull()
            val lon = prefs.getString("manual-longitude", null)?.toDoubleOrNull()
            if (!validPhotoCoordinates(lat, lon)) return null
            return PhotoManualLocation(prefs.getString("manual-name", "").orEmpty(), lat!!, lon!!)
        }
    fun saveLocation(name: String, latitude: Double, longitude: Double) {
        require(validPhotoCoordinates(latitude, longitude))
        prefs.edit().putString("manual-name", name.trim().take(100)).putString("manual-latitude", latitude.toString())
            .putString("manual-longitude", longitude.toString()).putBoolean("manual-location", true).apply()
    }
    fun key(source: PhotoWeatherSource): String = prefs.getString("key-${source.name}", "").orEmpty()
    fun saveKey(source: PhotoWeatherSource, key: String) { prefs.edit().putString("key-${source.name}", key.trim()).apply() }
    fun snapshot() = PhotoServiceConfig(placeSource, weatherSource, key(PhotoWeatherSource.AMAP), key(PhotoWeatherSource.SENIVERSE))

    fun captureFacts(facts: FrozenPhotoFacts, now: Long): FrozenPhotoFacts {
        if (!facts.fromCamera) return facts
        if (!recordCapture) return facts.copy(latitude = null, longitude = null, location = null, weather = null)
        if (!manual || facts.capturedAt?.let { now - it in 0..600_000L } != true) return facts
        val fixed = manualLocation ?: return facts
        return facts.copy(latitude = fixed.latitude, longitude = fixed.longitude, location = fixed.name.takeIf(String::isNotBlank))
    }
}
