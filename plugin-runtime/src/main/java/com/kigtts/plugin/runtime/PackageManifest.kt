package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.PLUGIN_API_VERSION
import kotlinx.serialization.Serializable

@Serializable
data class PackageManifest(val id: String, val name: String, val version: String, val versionCode: Int,
    val entryClass: String, val apiVersion: Int = PLUGIN_API_VERSION, val minSdk: Int = 26,
    val runtime: String = "android-dex", val license: String = "MIT",
    val capabilities: List<String> = emptyList(), val abis: List<String> = emptyList(),
    val files: Map<String, String>, val permissionPurposes: Map<String, String> = emptyMap())

data class PackagePreview(val manifest: PackageManifest, val publisherSha256: String)
data class InstalledPlugin(val manifest: PackageManifest, val publisherSha256: String, val enabled: Boolean)
