package com.kigtts.next.plugins.control

import com.kigtts.plugin.api.ManagementList
import com.kigtts.plugin.api.PluginPage

/** Keep the visible rows and their per-row download controls consistent, including cached catalogs. */
internal fun PluginPage.hideBundledEntries(ids: Set<String>): PluginPage {
    val list = management ?: return this
    if (list.items.none { it.id in ids }) return this
    return copy(management = list.withoutBundledEntries(ids), search = search)
}

private fun ManagementList.withoutBundledEntries(ids: Set<String>): ManagementList =
    copy(items = items.filterNot { it.id in ids }, itemActions = itemActions.filterKeys { it !in ids }, sectionActions = sectionActions)
