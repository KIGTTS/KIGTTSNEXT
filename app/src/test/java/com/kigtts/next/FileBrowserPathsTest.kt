package com.kigtts.next

import com.kigtts.next.ui.files.directoryCrumbs
import com.kigtts.next.ui.files.folderDestinationAllowed
import com.kigtts.next.ui.plugins.validBrowserName
import org.junit.Assert.*
import org.junit.Test

class FileBrowserPathsTest {
    @Test fun breadcrumbsAlwaysIncludeRootAndEveryReachableAncestor() {
        assertEquals(listOf("根目录" to ""), directoryCrumbs(""))
        assertEquals(listOf("根目录" to "", "assets" to "assets", "图片" to "assets/图片"), directoryCrumbs("assets/图片"))
    }
    @Test fun destinationsCannotBeASelectedFolderOrItsDescendant() {
        assertFalse(folderDestinationAllowed(listOf("assets"), "assets"))
        assertFalse(folderDestinationAllowed(listOf("assets", "photo.jpg"), "assets/deep"))
        assertTrue(folderDestinationAllowed(listOf("assets"), "assets2"))
        assertTrue(folderDestinationAllowed(listOf("image.jpg"), "assets"))
    }
    @Test fun renameRejectsNavigationAndKeepsNormalUnicodeFileNames() {
        listOf("", ".", "..", "../secret", "a\\b", "a/b", "a:b", "a\u0000b").forEach { assertFalse(validBrowserName(it)) }
        assertTrue(validBrowserName("照片 1.png")); assertTrue(validBrowserName("工具.lua"))
    }
}
