package com.kigtts.next.ui.plugins

internal data class PackageInstallerLabels(val action: String, val working: String, val result: String, val done: String)

internal fun installerLabels(packages: List<PackageInstallerInfo>): PackageInstallerLabels {
    val updates = packages.count { it.previous != null }
    return when {
        updates == 0 -> PackageInstallerLabels("安装插件", "正在安装…", "插件已安装", "安装完成")
        updates == packages.size -> PackageInstallerLabels("更新插件", "正在更新…", "插件已更新", "更新完成")
        else -> PackageInstallerLabels("安装并更新", "正在安装与更新…", "安装与更新已完成", "完成")
    }
}
