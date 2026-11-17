package com.kigtts.next

import com.kigtts.next.plugins.control.hideBundledEntries
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test

class StoreCatalogVisibilityTest {
    @Test fun hiddenBuiltInEntriesAlsoRemoveTheirDownloadActions() {
        val assistant = ManagementItem("tools.cloud-agent", "AI assistant")
        val external = ManagementItem("test.engine", "Engine")
        val download = ManagementProgressAction("Download", "download")
        val more = ManagementLoadMore("more")
        val page = PluginPage("store", "Store", emptyList(), ManagementList("catalog", listOf(assistant, external),
            style = ManagementListStyle.CATALOG, itemActions = mapOf(assistant.id to download, external.id to download), loadMore = more))
        val filtered = requireNotNull(page.hideBundledEntries(setOf(assistant.id)).management)
        assertEquals(listOf(external), filtered.items)
        assertEquals(mapOf(external.id to download), filtered.itemActions)
        assertEquals(more, filtered.loadMore)
    }
}
