package com.kigtts.next.cards

import org.json.JSONObject
import java.util.Locale

internal data class PhotoAmapPlace(val name: String?, val adcode: String?)

internal object PhotoAmapLocation {
    suspend fun read(latitude: Double, longitude: Double, key: String,
        fetch: PhotoJsonFetch = PhotoServiceHttp::get): PhotoAmapPlace? {
        if (key.isBlank() || !validPhotoCoordinates(latitude, longitude)) return null
        // EXIF and Android GPS use WGS84; AMap reverse geocoding expects GCJ-02.
        val original = "%.2f,%.2f".format(Locale.ROOT, longitude, latitude)
        val conversion = fetch(PhotoServiceHttp.query("https://restapi.amap.com/v3/assistant/coordinate/convert",
            "key" to key, "locations" to original, "coordsys" to "gps")) ?: return null
        if (conversion.optString("status") != "1") return null
        val converted = conversion.optString("locations")
        if (!converted.matches(Regex("-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?"))) return null
        val response = fetch(PhotoServiceHttp.query("https://restapi.amap.com/v3/geocode/regeo",
            "key" to key, "location" to converted, "extensions" to "base")) ?: return null
        return parse(response)
    }
    fun parse(response: JSONObject): PhotoAmapPlace? {
        if (response.optString("status") != "1") return null
        val address = response.optJSONObject("regeocode")?.optJSONObject("addressComponent") ?: return null
        // Empty AMap values can be arrays rather than strings.
        fun text(name: String) = (address.opt(name) as? String)?.trim()?.takeIf(String::isNotBlank)
        val name = listOfNotNull(text("province"), text("city"), text("district")).distinct().joinToString(" · ").ifBlank { null }
        return PhotoAmapPlace(name, text("adcode")?.takeIf { it.matches(Regex("\\d{6}")) })
    }
}
