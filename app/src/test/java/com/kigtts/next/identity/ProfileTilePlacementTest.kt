package com.kigtts.next.identity

import androidx.compose.ui.geometry.Rect
import com.kigtts.next.ui.identity.ProfileTileDrop
import com.kigtts.next.ui.identity.profileBoardGeometry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileTilePlacementTest {
    @Test fun dropSnapsIntoBlankColumnsBesideVariableSizedTiles() {
        val tiles = listOf(
            ProfileTile(id = "title", kind = ProfileTileKind.TITLE),
            ProfileTile(id = "square-link", kind = ProfileTileKind.CONTACT, columns = 1),
            ProfileTile(id = "photo", kind = ProfileTileKind.IMAGE, columns = 2, rows = 2),
            ProfileTile(id = "qr", kind = ProfileTileKind.QR, columns = 2, rows = 2))
        val geometry = profileBoardGeometry(tiles, 360f, 12f, 36f)
        val qr = geometry.bounds.getValue("qr")
        val row = geometry.rowTops.lastIndex
        val top = geometry.rowTops[row]
        val left = 2 * geometry.pitch
        val result = ProfileTileDrop(tiles, "qr", geometry).at(Rect(left, top, left + qr.width, top + qr.height))
        val positions = packProfileTiles(result)
        assertEquals(2, positions.single { it.tile.id == "qr" }.column)
        assertEquals(360f - qr.width, profileBoardGeometry(result, 360f, 12f, 36f).bounds.getValue("qr").left, .01f)
        val occupied = positions.flatMap { p -> (p.row until p.row + p.tile.rows).toList() }.distinct().sorted()
        assertEquals((0..occupied.last()).toList(), occupied)
    }

    @Test fun dividerIsHalfHeightAndSparseCoordinatesNeverCreateEntireBlankRows() {
        val divider = ProfileTile(id = "divider", kind = ProfileTileKind.DIVIDER, title = "", dividerStyle = ProfileDividerStyle.WAVE)
        val floating = ProfileTile(id = "far", columns = 1, rows = 1, columnHint = 3, rowHint = 50)
        val restored = ProfileTile.from(divider.json())
        assertEquals(ProfileDividerStyle.WAVE, restored.dividerStyle)
        assertEquals("", restored.title)
        val tiles = listOf(ProfileTile(id = "top"), restored, ProfileTile.from(floating.json()))
        val geometry = profileBoardGeometry(tiles, 360f, 12f, 36f)
        assertEquals(geometry.unit / 2, geometry.bounds.getValue("divider").height, .01f)
        assertEquals(2, packProfileTiles(tiles).single { it.tile.id == "far" }.row)
        assertEquals(3, packProfileTiles(tiles).single { it.tile.id == "far" }.column)
    }

    @Test fun darkQrCanStayLightAndSurvivesArchiveSanitizing() {
        val dark = profileQrPalette(ProfileQrStyle.THEME, false, true, 0xffc2bfff.toInt(), 0xff32303b.toInt())
        val light = profileQrPalette(ProfileQrStyle.THEME, true, true, 0xffc2bfff.toInt(), 0xff32303b.toInt())
        assertTrue(androidx.core.graphics.ColorUtils.calculateLuminance(dark.paper) < androidx.core.graphics.ColorUtils.calculateLuminance(dark.ink))
        assertTrue(androidx.core.graphics.ColorUtils.calculateLuminance(light.paper) > androidx.core.graphics.ColorUtils.calculateLuminance(light.ink))
        val tile = ProfileTile(kind = ProfileTileKind.QR, text = "中文", qrKeepLight = true)
        val metadata = org.json.JSONObject().put("nickname", "测试")
            .put("tiles", org.json.JSONArray(listOf(tile.json())))
        assertTrue(profileTiles(requireNotNull(OfflineProfileData.snapshot(metadata))).single().qrKeepLight)
    }
}
