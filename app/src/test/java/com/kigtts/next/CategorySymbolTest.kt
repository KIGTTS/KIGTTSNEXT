package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.icons.MaterialSymbolCatalog
import com.kigtts.next.plugins.quicktext.planBatch
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategorySymbolTest {
    @Test fun arbitraryCatalogSymbolSurvivesRenameSaveReloadAndPortableSnapshot() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("quick-phrases", 0).edit().clear().commit()
        val library = QuickTextLibrary(QuickPhraseStore(app))
        val symbol = CategoryIcon.symbol("two_wheeler")
        assertFalse("This verifies the full catalog rather than only curated icons", "two_wheeler" in CategoryIcon.commonSymbols)
        assertTrue(library.addCategory("摩托", symbol))
        assertTrue(library.editCategory("摩托", "骑行", symbol))
        val restored = QuickTextLibrary(QuickPhraseStore(app))
        assertEquals("symbol:two_wheeler", restored.categoryIcons.value["骑行"]?.name)
        assertEquals(symbol, restored.categoryIcons.value["骑行"])
        assertEquals(CategoryIcon.INFO, restored.categoryIcons.value["常用"])
        val group = QuickGroup(name = "骑行", icon = symbol.name)
        assertEquals(group, PluginJson.decodeFromString<QuickGroup>(PluginJson.encodeToString(group)))
    }
    @Test fun pluginBatchAcceptsRealSymbolsAndRejectsUnknownNamesBeforeMutation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        MaterialSymbolCatalog.initialize(app)
        val group = QuickGroup(name = "测试")
        val command = QuickTextCommand.Prepare("symbol-test", 0, listOf(QuickTextEdit.PutGroup(group.copy(icon = "symbol:two_wheeler"))))
        val plan = planBatch("test.plugin", command, listOf(group), emptyList(), 0, emptyMap())
        assertEquals("symbol:two_wheeler", plan.groups.single().icon)
        assertEquals("LIST", group.icon)
        assertThrows(IllegalArgumentException::class.java) {
            planBatch("test.plugin", command.copy(edits = listOf(QuickTextEdit.PutGroup(group.copy(icon = "symbol:not_a_real_material_symbol_9876")))), listOf(group), emptyList(), 0, emptyMap())
        }
    }
    @Test fun everyCommonIconIsAvailableInTheSharedOfflineLibrary() {
        MaterialSymbolCatalog.initialize(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(CategoryIcon.commonSymbols.size >= 60)
        CategoryIcon.commonSymbols.forEach { assertTrue("Missing legacy picker icon: $it", MaterialSymbolCatalog.contains(it)) }
        assertFalse(CategoryIcon.valid("symbol:../../private"))
    }
}
