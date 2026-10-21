package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*

/** An old Kotlin default-constructor call picks up the host's current default API at runtime. */
internal fun packageDescriptor(actual: PluginDescriptor, manifest: PackageManifest): PluginDescriptor {
    require(actual.id == manifest.id && actual.version == manifest.version && actual.license == manifest.license) { "插件声明与安装包不一致" }
    require(manifest.apiVersion in MIN_PLUGIN_API_VERSION..PLUGIN_API_VERSION) { "插件接口版本不兼容" }
    require(actual.apiVersion == manifest.apiVersion || actual.apiVersion == PLUGIN_API_VERSION) { "插件声明的接口版本不匹配" }
    return actual.copy(apiVersion = manifest.apiVersion)
}
