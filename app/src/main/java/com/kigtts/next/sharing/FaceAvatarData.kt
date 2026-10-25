package com.kigtts.next.sharing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

internal fun faceAvatarData(file: File): String? = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 128).coerceAtLeast(1); options.inJustDecodeBounds = false
    val source = BitmapFactory.decodeFile(file.path, options) ?: return@runCatching null
    try {
        val thumbnail = Bitmap.createScaledBitmap(source, 96, 96, true)
        try { ByteArrayOutputStream().use { output ->
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 82, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } } finally { if (thumbnail !== source) thumbnail.recycle() }
    } finally { source.recycle() }
}.getOrNull()
