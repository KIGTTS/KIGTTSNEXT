package com.kigtts.next.identity

import com.kigtts.next.ui.identity.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class FavoriteSearchTest {
    @Test fun searchCombinesNameBioAndTagsAndIgnoresMalformedSnapshots() {
        val good = SavedContact("a", JSONObject().put("nickname", "Alice 摄影").put("bio", "广州")
            .put("tags", org.json.JSONArray(listOf("Kigurumi"))).toString(), 1, null)
        val index = indexFavorites(listOf(good, SavedContact("bad", "{", 1, null)))
        assertEquals(1, index.size)
        assertEquals(listOf("a"), searchFavorites(index, " ALICE   Kigurumi 广州 ").map { it.item.id })
        assertTrue(searchFavorites(index, "不存在").isEmpty())
        assertSame(index, searchFavorites(index, "  "))
    }
}
