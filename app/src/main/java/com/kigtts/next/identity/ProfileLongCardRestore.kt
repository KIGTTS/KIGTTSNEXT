package com.kigtts.next.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.kigtts.next.sharing.faceAvatarData
import org.json.JSONArray
import java.io.File
import kotlin.math.roundToInt

internal object ProfileLongCardRestore {
    const val MAX_BYTES = 96L * 1024 * 1024

    fun imageFile(file: File): Boolean {
        if (file.length() !in 8..MAX_BYTES) return false
        return file.inputStream().use {
            when (it.read()) {
                0x89 -> it.read() == 0x50 && it.read() == 0x4e && it.read() == 0x47
                0xff -> it.read() == 0xd8 && it.read() == 0xff
                else -> false
            }
        }
    }

    fun restore(file: File, directory: File, favorites: ContactFavorites): SavedContact? {
        if (!imageFile(file)) return null
        directory.mkdirs()
        @Suppress("DEPRECATION")
        val decoder = runCatching { BitmapRegionDecoder.newInstance(file.path, false) }.getOrNull() ?: return null
        try {
            val width = decoder.width
            val height = decoder.height
            if (width !in 420..4096 || height !in (width / 2)..40000) return null
            val source = File(directory, "embedded.mhtml")
            if (ProfilePngPayload.extract(file, source)) {
                runCatching { ProfileArchive.restore(source, File(directory, "embedded"), favorites) }.getOrNull()?.let { return it }
            }
            val json = ProfileGrayCode.decode(width, height) { band ->
                if (width.toLong() * band > 24_000_000) null
                else runCatching { decoder.decodeRegion(Rect(0, height - band, width, height), options()) }.getOrNull()
            } ?: return null
            val data = OfflineProfileData.parse(json) ?: return null
            val known = data.optJSONObject("profile")?.let(OfflineProfileData::snapshot) ?: return null
            known.remove("avatar")
            val tiles = profileTiles(known)
            known.put("tiles", profileTileJson(tiles.map { it.copy(image = "") }))
            val coverBounds = decoder.bounds(data.optJSONArray("cover"))
            val cover = coverBounds?.let { decoder.saveCrop(it, File(directory, "crop.jpg")) }
            if (known.optBoolean("coverIsBackground") && coverBounds != null) {
                val avatarBounds = decoder.bounds(data.optJSONArray("avatarCrop"))
                if (avatarBounds != null && avatarBounds.top >= coverBounds.bottom && avatarBounds.width() <= width / 3 &&
                    kotlin.math.abs(avatarBounds.width() - avatarBounds.height()) <= 1) {
                    runCatching {
                        decoder.decodeRegion(avatarBounds, options())?.let { bitmap ->
                            try { faceAvatarData(bitmap) } finally { bitmap.recycle() }
                        }
                    }.getOrNull()?.let { known.put("avatar", it) }
                }
            }
            val photos = linkedMapOf<String, File>()
            data.optJSONArray("tileCrops")?.let { crops ->
                for (index in 0 until minOf(crops.length(), 24)) {
                    val item = crops.optJSONObject(index) ?: continue
                    val id = item.opt("id") as? String ?: continue
                    val tile = tiles.firstOrNull { it.id == id && it.kind == ProfileTileKind.IMAGE } ?: continue
                    val rect = decoder.bounds(item.opt("left"), item.opt("top"), item.opt("right"), item.opt("bottom")) ?: continue
                    decoder.saveCrop(rect, File(directory, "tile-$index.jpg"))?.let { photos[tile.id] = it }
                }
            }
            return favorites.save(known, cover, photos)
        } finally { decoder.recycle() }
    }

    private fun BitmapRegionDecoder.bounds(value: JSONArray?): Rect? = value?.takeIf { it.length() == 4 }
        ?.let { bounds(it.opt(0), it.opt(1), it.opt(2), it.opt(3)) }

    private fun BitmapRegionDecoder.bounds(l: Any?, t: Any?, r: Any?, b: Any?): Rect? {
        val scale = width.toDouble() / ProfileGrayCode.WIDTH
        fun coordinate(value: Any?, limit: Int): Int? = (value as? Int)?.times(scale)
            ?.takeIf { it >= 0 && it <= limit + .5 }?.roundToInt()?.takeIf { it <= limit }
        val rect = Rect(coordinate(l, width) ?: return null, coordinate(t, height) ?: return null,
            coordinate(r, width) ?: return null, coordinate(b, height) ?: return null)
        return rect.takeIf { !it.isEmpty && it.height() <= width * 2 }
    }

    private fun BitmapRegionDecoder.saveCrop(rect: Rect, target: File): File? {
        var sample = 1
        while (maxOf(rect.width(), rect.height()) / sample > 1600) sample *= 2
        val bitmap = runCatching { decodeRegion(rect, options(sample)) }.getOrNull() ?: return null
        try {
            if (target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) } &&
                target.length() in 1..(2L * 1024 * 1024)) return target
            target.delete()
            return null
        } finally { bitmap.recycle() }
    }

    private fun options(sample: Int = 1) = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = sample
    }
}
