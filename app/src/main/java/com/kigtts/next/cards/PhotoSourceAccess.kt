package com.kigtts.next.cards

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.kigtts.next.media.WebAndroidPermissions
import java.io.InputStream

/** Prefer the selected original's EXIF when permitted; a denied location grant never blocks import. */
internal suspend fun openPhotoSource(context: Context, uri: Uri): InputStream {
    val media = uri.scheme == "content" && uri.authority in setOf(MediaStore.AUTHORITY, "com.android.providers.media.documents")
    if (Build.VERSION.SDK_INT >= 29 && media && PhotoFactsConsent.request(context, "location")) {
        val prefs = context.getSharedPreferences("polaroid-facts", 0)
        fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted() && !prefs.getBoolean("media-location-asked", false)) {
            prefs.edit().putBoolean("media-location-asked", true).apply()
            WebAndroidPermissions.request(context, listOf(Manifest.permission.ACCESS_MEDIA_LOCATION))
        }
        if (granted()) {
            try {
                val mediaUri = if (uri.authority == MediaStore.AUTHORITY) uri else MediaStore.getMediaUri(context, uri)
                mediaUri?.let { context.contentResolver.openInputStream(MediaStore.setRequireOriginal(it)) }?.let { return it }
            } catch (_: SecurityException) { /* The picker may explicitly exclude location. */ }
            catch (_: UnsupportedOperationException) { }
            catch (_: java.io.IOException) { }
        }
    }
    return requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选照片" }
}
