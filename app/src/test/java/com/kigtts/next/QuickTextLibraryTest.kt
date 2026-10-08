package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickTextLibraryTest {
    @Test fun categoryRenameAndIconChangesPreserveIdsOrderAndRejectDuplicates() {
        val app = app(); val library = QuickTextLibrary(QuickPhraseStore(app))
        val items = library.phrases.value
        assertFalse(library.editCategory("常用", "生活", CategoryIcon.HOME))
        assertFalse(library.editCategory("常用", " ", CategoryIcon.HOME))
        assertTrue(library.editCategory("常用", " 常用交流 ", CategoryIcon.MESSAGE))
        val restored = QuickTextLibrary(QuickPhraseStore(app))
        assertEquals(listOf("常用交流", "生活", "收藏", "出行"), restored.categories.value)
        assertEquals(items.map { it.id }, restored.phrases.value.map { it.id })
        assertTrue(restored.phrases.value.none { it.category == "常用" })
        assertEquals(CategoryIcon.MESSAGE, restored.categoryIcons.value["常用交流"])
        assertFalse(restored.categoryIcons.value.containsKey("常用"))
        assertEquals("常用交流", testAppModel(app).state.value.selectedCategory)
    }
    @Test fun transferringToEmptyGroupsKeepsIdentityAndPersistsDestinationOrder() {
        val app = app(); val library = QuickTextLibrary(QuickPhraseStore(app))
        val original = library.phrases.value; val item = original.first()
        library.addCategory("空组", CategoryIcon.WORK)
        assertFalse(library.transferPhrase(item.id, "不存在"))
        assertTrue(library.transferPhrase(item.id, "空组"))
        assertFalse(library.transferPhrase(item.id, "空组"))
        assertEquals(item.copy(category = "空组"), library.phrases.value.last())
        assertTrue(library.transferPhrase(item.id, "生活"))
        val restored = QuickTextLibrary(QuickPhraseStore(app))
        assertEquals(item.id, restored.phrases.value.filter { it.category == "生活" }.last().id)
        assertEquals(1, restored.phrases.value.count { it.id == item.id })
        assertEquals(original.filterNot { it.id == item.id }.map { it.id }, restored.phrases.value.filterNot { it.id == item.id }.map { it.id })
    }
    private fun app() = ApplicationProvider.getApplicationContext<Application>().also {
        it.getSharedPreferences("quick-phrases", 0).edit().clear().commit()
    }
    @Test fun legacyDuplicatesReceiveStableIdsAndCanBeDeletedIndependently() {
        val app = app()
        app.getSharedPreferences("quick-phrases", 0).edit().putString("items",
            "[{\"text\":\"相同文字\",\"category\":\"常用\"},{\"text\":\"相同文字\",\"category\":\"常用\"}]").commit()
        val library = QuickTextLibrary(QuickPhraseStore(app))
        val items = library.phrases.value
        assertNotEquals(items[0].id, items[1].id)
        assertEquals(items, QuickTextLibrary(QuickPhraseStore(app)).phrases.value)
        library.remove(items[0].id)
        assertEquals(listOf(items[1]), QuickPhraseStore(app).load())
    }
    @Test fun itemAndGroupOrderPersistWithoutChangingOtherGroups() {
        val app = app()
        val library = QuickTextLibrary(QuickPhraseStore(app))
        val original = library.phrases.value
        val sameGroup = original.filter { it.category == "常用" }
        library.movePhrase(sameGroup[0].id, sameGroup[3].id)
        library.moveCategory("出行", "常用")
        val restored = QuickTextLibrary(QuickPhraseStore(app))
        assertEquals(library.phrases.value, restored.phrases.value)
        assertEquals(listOf("出行", "常用", "生活", "收藏"), restored.categories.value)
        assertEquals(sameGroup[0].id, restored.phrases.value.filter { it.category == "常用" }[3].id)
        assertEquals(original.filter { it.category != "常用" }, restored.phrases.value.filter { it.category != "常用" })
    }
    @Test fun emptyCustomGroupsAndDetailEditsSurviveRestart() {
        val app = app()
        val library = QuickTextLibrary(QuickPhraseStore(app))
        assertTrue(library.addCategory("自定义"))
        assertFalse(library.addCategory(" 自定义 "))
        assertFalse(library.addCategory(" "))
        val item = library.phrases.value.first()
        library.edit(item.id, "修改后的文字", "辅助说明", "自定义")
        val restored = QuickTextLibrary(QuickPhraseStore(app))
        val edited = restored.phrases.value.first { it.id == item.id }
        assertEquals("修改后的文字", edited.text)
        assertEquals("辅助说明", edited.description)
        assertEquals("自定义", edited.category)
        assertTrue(restored.addCategory("空分组"))
        assertTrue("空分组" in QuickTextLibrary(QuickPhraseStore(app)).categories.value)
    }
    @Test fun selectedIconsStayWithGroupsAfterSortingItemEditsAndRestart() {
        val app = app()
        val library = QuickTextLibrary(QuickPhraseStore(app))
        assertTrue(library.addCategory("旅行交流", CategoryIcon.TRAVEL))
        assertFalse(library.addCategory(" 旅行交流 ", CategoryIcon.SHOPPING))
        library.moveCategory("旅行交流", "常用")
        library.add("问路", "旅行交流")
        val item = library.phrases.value.first()
        library.edit(item.id, "问候", "说明", "旅行交流")
        library.remove(library.phrases.value.last().id)
        val restored = QuickTextLibrary(QuickPhraseStore(app))
        assertEquals("旅行交流", restored.categories.value.first())
        assertEquals(CategoryIcon.TRAVEL, restored.categoryIcons.value["旅行交流"])
        assertEquals(library.categoryIcons.value, restored.categoryIcons.value)
        assertEquals(library.phrases.value, restored.phrases.value)
    }
    @Test fun olderGroupsAndUnknownIconKeysRetainTheirDefaultIcons() {
        val app = app()
        app.getSharedPreferences("quick-phrases", 0).edit()
            .putString("categories", "[\"常用\",\"生活\",\"收藏\",\"出行\",\"旅行\"]")
            .putString("category-icons", "{\"常用\":\"unknown-future-icon\",\"旅行\":\"TRAVEL\"}").commit()
        val library = QuickTextLibrary(QuickPhraseStore(app))
        assertEquals(CategoryIcon.INFO, library.categoryIcons.value["常用"])
        assertEquals(CategoryIcon.PERSON, library.categoryIcons.value["生活"])
        assertEquals(CategoryIcon.FAVORITE, library.categoryIcons.value["收藏"])
        assertEquals(CategoryIcon.LIST, library.categoryIcons.value["出行"])
        assertEquals(CategoryIcon.TRAVEL, library.categoryIcons.value["旅行"])
        assertEquals(library.categoryIcons.value, QuickTextLibrary(QuickPhraseStore(app)).categoryIcons.value)
    }
}
