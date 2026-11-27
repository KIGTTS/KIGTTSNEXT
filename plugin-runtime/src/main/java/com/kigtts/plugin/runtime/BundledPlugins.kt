package com.kigtts.plugin.runtime

import android.content.Context
import java.io.File

/** APK resources loaded by the same signed, isolated worker path as installed packages. */
data class BundledPlugin(val id: String, val asset: String, val versionCode: Int, val publisher: String)

internal class BundledPlugins(private val context: Context, private val packages: PluginPackageStore,
    private val specs: List<BundledPlugin>, private val optedIn: (String) -> Boolean) {
    val ids = specs.mapTo(mutableSetOf()) { it.id }
    private val assetManifests = mutableMapOf<String, PackageManifest>()
    fun allowed(id: String) = id !in ids || optedIn(id)
    fun verified(info: InstalledPlugin) = optedIn(info.manifest.id) && specs.any {
        it.id == info.manifest.id && it.publisher == info.publisherSha256 && it.versionCode == info.manifest.versionCode
    }

    /** Install only after opt-in. Keep the existing same-publisher data directory and grants. */
    fun prepare(): Map<String, String> = buildMap {
        val installed = packages.installed().associateBy { it.manifest.id }
        for (spec in specs.filter { optedIn(it.id) }) {
            val old = installed[spec.id]
            if (old?.publisherSha256 == spec.publisher && old.manifest == assetManifests[spec.id]) continue
            try {
                require(old == null || old.publisherSha256 == spec.publisher) { "publisher" }
                val file = File.createTempFile("bundled-plugin-", ".kigplugin", context.cacheDir)
                try {
                    context.assets.open(spec.asset).use { input -> file.outputStream().use { input.copyTo(it) } }
                    val preview = packages.inspect(file)
                    require(preview.manifest.id == spec.id && preview.manifest.versionCode == spec.versionCode)
                    require(preview.publisherSha256 == spec.publisher)
                    if (old?.manifest != preview.manifest) packages.installBundled(file, spec.publisher)
                    assetManifests[spec.id] = preview.manifest
                } finally { file.delete() }
            } catch (error: Exception) {
                android.util.Log.e("BundledPlugins", "Could not prepare ${spec.id}", error)
                put(spec.id, "bundle-prepare-failed")
            }
        }
    }
}
