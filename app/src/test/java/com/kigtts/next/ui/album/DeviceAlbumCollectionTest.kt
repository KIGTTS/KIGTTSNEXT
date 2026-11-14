package com.kigtts.next.ui.album

import android.net.Uri
import com.kigtts.next.media.AlbumMedia
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceAlbumCollectionTest {
    @Test fun commonCollectionsLeadAndOtherFoldersFollowRecentImports() {
        val rows = listOf(photo("old", 1), photo("新版图", 99), photo("QQ", 2), photo("Camera", 2),
            photo("KIGTTS", 3), photo("其他", 3, true))
        val albums = albumCollections(rows, "全部照片")
        assertEquals(listOf("全部照片", "KIGTTS", "Camera", "收藏夹", "QQ", "新版图", "其他", "old"),
            albums.map(AlbumCollection::name))
        assertEquals(listOf(rows.last()), albums.single { it.id == "favorites" }.media)
    }
    private fun photo(folder: String, added: Long, favorite: Boolean = false) =
        AlbumMedia(Uri.parse("content://photos/$folder"), folder, "image/jpeg", 10, 12, 14,
            bucketId = folder, bucketName = folder, favorite = favorite, addedAt = added)
}
