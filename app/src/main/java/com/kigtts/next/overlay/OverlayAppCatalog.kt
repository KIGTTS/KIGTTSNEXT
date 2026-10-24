package com.kigtts.next.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Listing only reads labels. Icons are loaded lazily for visible rows, off the UI thread. */
internal object OverlayAppCatalog {
    private val lock = Mutex()
    private var cached: List<OverlayShortcut> = emptyList()
    private var packageStamp: List<Pair<String, Long>> = emptyList()
    private val images = LruCache<String, Bitmap>(96)
    fun cachedIcon(entry: OverlayShortcut): Bitmap? = entry.iconPath?.let { synchronized(images) { images.get(it) } }

    suspend fun list(context: Context): List<OverlayShortcut> = withContext(Dispatchers.IO) { lock.withLock {
        val pm = context.packageManager
        val results = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled && it.activityInfo.packageName != context.packageName }
        val versions = results.map { it.activityInfo.packageName }.distinct().associateWith { name ->
            runCatching { pm.getPackageInfo(name, 0).lastUpdateTime }.getOrDefault(0L)
        }
        val stamp = results.map { result -> ComponentName(result.activityInfo.packageName, result.activityInfo.name).flattenToString() to versions.getValue(result.activityInfo.packageName) }.sortedBy { it.first }
        if (stamp != packageStamp || cached.isEmpty()) {
            cached = results.map { result ->
                val component = ComponentName(result.activityInfo.packageName, result.activityInfo.name).flattenToString()
                val identity = "$component:${versions[result.activityInfo.packageName]}"
                val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                val path = File(context.filesDir, "overlay-icon-cache/$digest.png").absolutePath
                OverlayShortcut("app:$component", result.loadLabel(pm).toString(), "apps", component = component, category = "应用", iconPath = path)
            }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
            packageStamp = stamp
        }
        cached
    } }

    suspend fun icon(context: Context, entry: OverlayShortcut): Bitmap? = withContext(Dispatchers.IO) {
        val path = entry.iconPath ?: return@withContext null
        synchronized(images) { images.get(path) }?.let { return@withContext it }
        val target = File(path)
        val bitmap = runCatching {
            BitmapFactory.decodeFile(path) ?: run {
                val component = ComponentName.unflattenFromString(entry.component ?: return@runCatching null) ?: return@runCatching null
                val drawable = context.packageManager.getActivityIcon(component)
                val rendered = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 96, 96); drawable.draw(Canvas(rendered))
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, "${target.name}.part-${Thread.currentThread().id}")
                try { temporary.outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }; temporary.renameTo(target) }
                finally { temporary.delete() }
                rendered
            }
        }.getOrNull()
        if (bitmap != null) synchronized(images) { images.put(path, bitmap) }
        bitmap
    }
}
