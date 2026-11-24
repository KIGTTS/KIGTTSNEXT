package com.kigtts.next.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import com.kigtts.next.legacy.LegacyArchive
import com.kigtts.next.legacy.legacyJson
import com.kigtts.next.theme.AppearanceAssets
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal const val CONFIG_BACKUP_TYPE = "kigtts_next_config_backup"

internal data class ConfigRestore(val values: ConfigValues, val assets: Map<String, File>, val staging: File)

internal class AppConfigArchive(private val context: Context) {
    private val preferences = AppConfigPreferences(context)

    fun write(output: File) {
        val appearance = AppearanceAssets(context)
        val snapshot = preferences.snapshot().toMutableMap()
        val assets = AppConfigPreferences.assets(snapshot).mapNotNull { key -> appearance.file(key)?.let { key to it } }.toMap()
        snapshot["appearance"] = snapshot.getValue("appearance").filter { (key, value) ->
            key !in setOf("font", "background") || value in assets.keys
        }
        snapshot["appearance-fonts"] = snapshot.getValue("appearance-fonts").filterKeys { it in assets }
        require(assets.size <= MAX_ASSETS && assets.values.sumOf(File::length) <= MAX_ASSET_BYTES)
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.text("manifest.json", JSONObject().put("type", CONFIG_BACKUP_TYPE).put("version", 1).toString())
            zip.text("settings.json", AppConfigPreferences.encode(snapshot).toString())
            assets.forEach { (key, file) ->
                zip.putNextEntry(ZipEntry("appearance/$key"))
                file.inputStream().use { LegacyArchive.copy(it, zip, AppearanceAssets.MAX_FONT.toLong()) }
                zip.closeEntry()
            }
        }
    }

    fun read(source: File, staging: File): ConfigRestore = ZipFile(source).use { zip ->
        val entries = zip.backupEntries(MAX_ASSET_BYTES + LegacyArchive.MAX_METADATA, MAX_ASSETS + 2)
        val manifest = legacyJson(zip.readBackupText(entries.getValue("manifest.json")))
        require(manifest.optString("type") == CONFIG_BACKUP_TYPE && manifest.opt("version") == 1)
        val values = preferences.decode(legacyJson(zip.readBackupText(entries.getValue("settings.json"))))
        require(values.isNotEmpty())
        val assets = AppConfigPreferences.assets(values).associateWith { key ->
            val entry = entries.getValue("appearance/$key")
            File(staging, key).also { file ->
                file.outputStream().use { zip.copyBackupEntry(entry, it, AppearanceAssets.MAX_FONT.toLong()) }
                verifyAsset(key, file)
            }
        }
        ConfigRestore(values, assets, staging)
    }

    fun restore(snapshot: ConfigRestore) {
        val root = File(context.filesDir, "appearance-assets").apply { mkdirs() }
        snapshot.assets.forEach { (key, file) -> copyAsset(key, file, root) }
        preferences.apply(snapshot.values, replace = true)
    }

    private fun copyAsset(key: String, file: File, target: File) {
        val destination = File(target, key)
        if (destination.isFile && digest(destination) == key.substringBefore('.')) return
        val temporary = File.createTempFile("restore-", ".tmp", target)
        try {
            file.copyTo(temporary, overwrite = true)
            Files.move(temporary.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    private fun verifyAsset(key: String, file: File) {
        require(digest(file) == key.substringBefore('.')) { "备份资源校验失败" }
        if (key.endsWith(".jpg")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096)
        } else {
            Typeface.createFromFile(file)
        }
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ZipOutputStream.text(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    companion object {
        private const val MAX_ASSETS = 128
        private const val MAX_ASSET_BYTES = 2L * 1024 * 1024 * 1024
    }
}
