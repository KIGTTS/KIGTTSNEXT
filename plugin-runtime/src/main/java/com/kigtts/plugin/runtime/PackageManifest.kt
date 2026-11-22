package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.PLUGIN_API_VERSION
import kotlinx.serialization.Serializable

@Serializable
data class PackageManifest(val id: String, val name: String, val version: String, val versionCode: Int,
    val entryClass: String, val apiVersion: Int = PLUGIN_API_VERSION, val minSdk: Int = 26,
    val runtime: String = "android-dex", val license: String = "MIT",
    val capabilities: List<String> = emptyList(), val abis: List<String> = emptyList(),
    val files: Map<String, String>, val permissionPurposes: Map<String, String> = emptyMap(),
    val icon: PackageIcon? = null)

/** Signed presentation only. File paths are package-relative, never URLs or plugin code. */
@Serializable
data class PackageIcon(val symbol: String? = null, val path: String? = null) {
    internal fun validate(files: Map<String, String>) {
        require((symbol != null) != (path != null)) { "插件图标需选择Material Symbol或包内图片" }
        symbol?.let { require(it.matches(Regex("[a-z][a-z0-9_]{0,79}"))) { "插件图标名称无效" } }
        path?.let {
            require(PackageVerifier.safeName(it) && it.startsWith("assets/") && it in files &&
                it.substringAfterLast('.').lowercase() in setOf("svg", "png", "jpg", "jpeg", "webp")) { "插件图标需为清单内的SVG、PNG、JPEG或WebP图片" }
        }
    }
}

data class PackagePreview(val manifest: PackageManifest, val publisherSha256: String)
data class InstalledPlugin(val manifest: PackageManifest, val publisherSha256: String, val enabled: Boolean, val development: Boolean = false)
