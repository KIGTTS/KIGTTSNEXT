package com.kigtts.next.cards

import android.content.Context
import java.util.Locale
import kotlin.math.*

/** Offline nearest-city fallback; explicitly labelled nearby rather than an exact address. */
internal object PhotoPlaceIndex {
    private data class Place(val latitude: Double, val longitude: Double, val local: String, val name: String)
    @Volatile private var cached: List<Place>? = null
    private fun places(context: Context): List<Place> = cached ?: synchronized(this) {
        cached ?: context.assets.open("photo-facts/cities.tsv").bufferedReader(Charsets.UTF_8).useLines { rows ->
            rows.filter { it.isNotBlank() && !it.startsWith('#') }.map { row ->
                val fields = row.split('\t'); require(fields.size == 5)
                Place(fields[1].toDouble(), fields[2].toDouble(), fields[3], fields[4])
            }.toList()
        }.also { cached = it }
    }
    fun nearby(context: Context, latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): String? {
        val name = city(context, latitude, longitude, locale) ?: return null
        return if (locale.language == "zh") "$name 附近" else "Near $name"
    }
    /** Plain city name for explicit weather queries; never fall back to the requester's IP. */
    fun city(context: Context, latitude: Double, longitude: Double, locale: Locale = Locale.SIMPLIFIED_CHINESE): String? {
        if (!validPhotoCoordinates(latitude, longitude)) return null
        val nearest = places(context).asSequence()
            .filter { abs(it.latitude - latitude) < .3 && abs(it.longitude - longitude) < .5 }
            .map { it to distance(latitude, longitude, it.latitude, it.longitude) }.minByOrNull { it.second }
            ?.takeIf { it.second <= 25 }?.first ?: return null
        return if (locale.language == "zh") nearest.local else nearest.name
    }
    private fun distance(lat: Double, lon: Double, otherLat: Double, otherLon: Double): Double {
        val a = sin(Math.toRadians(otherLat - lat) / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(otherLat)) * sin(Math.toRadians(otherLon - lon) / 2).pow(2)
        return 6371 * 2 * asin(sqrt(a.coerceIn(0.0,1.0)))
    }
}
