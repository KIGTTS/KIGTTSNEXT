package com.kigtts.next.ui.plugins

internal data class PackageInstallerLabels(val actionKey: String, val workingKey: String, val resultKey: String, val doneKey: String)

internal fun installerLabels(packages: List<PackageInstallerInfo>): PackageInstallerLabels {
    val updates = packages.count { it.previous != null }
    return when {
        updates == 0 -> PackageInstallerLabels("plugin.install", "plugin.installing", "store.installed", "store.install_done")
        updates == packages.size -> PackageInstallerLabels("plugin.update_action", "plugin.updating", "plugin.updated", "plugin.update_finished")
        else -> PackageInstallerLabels("plugin.install_and_update", "plugin.installing_and_updating", "plugin.install_update_finished", "action.done")
    }
}
