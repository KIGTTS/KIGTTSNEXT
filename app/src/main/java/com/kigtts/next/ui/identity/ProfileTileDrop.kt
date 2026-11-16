package com.kigtts.next.ui.identity

import androidx.compose.ui.geometry.Rect
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.identity.isSectionRow
import com.kigtts.next.identity.packProfileTiles
import kotlin.math.abs
import kotlin.math.roundToInt

/** Resolve against the settled grid at drag start; reflowing neighbors cannot change the active drop target. */
internal class ProfileTileDrop(private val tiles: List<ProfileTile>, private val id: String,
    private val geometry: ProfileBoardGeometry) {
    private val tilesAndPositions = packProfileTiles(tiles)
    private val moving = tilesAndPositions.first { it.tile.id == id }
    private val marks = tilesAndPositions.filter { it.tile.kind.isSectionRow }
    private val others = tilesAndPositions.filter { it.tile.id != id }
    private fun sectionStart(row: Int) = (marks.lastOrNull { it.row < row }?.row ?: -1) + 1
    private val anchored = others.associate { position ->
        val tile = position.tile
        tile.id to if (tile.kind.isSectionRow) tile else tile.copy(
            columnHint = position.column, rowHint = position.row - sectionStart(position.row))
    }

    fun at(floating: Rect): List<ProfileTile> {
        val dragged = moving.tile
        if (dragged.kind.isSectionRow) {
            val target = others.minByOrNull { abs(geometry.bounds.getValue(it.tile.id).center.y - floating.center.y) }
                ?: return tiles
            val targetY = geometry.bounds.getValue(target.tile.id).center.y
            val startY = geometry.bounds.getValue(id).center.y
            if (abs(targetY - floating.center.y) >= abs(startY - floating.center.y)) return tiles
            return reordered(target.tile.id, dragged)
        }
        val column = (floating.left / geometry.pitch).roundToInt().coerceIn(0, 4 - dragged.columns)
        val row = geometry.rowTops.indices.minBy { abs(geometry.rowTops[it] - floating.top) }
        if (row == moving.row && column == moving.column) return tiles
        val overlap = others.filter { p ->
            column < p.column + p.tile.columns && column + dragged.columns > p.column &&
                row < p.row + p.tile.rows && row + dragged.rows > p.row
        }
        val heading = overlap.firstOrNull { it.tile.kind.isSectionRow }
        if (heading != null) return reordered(heading.tile.id, dragged.copy(columnHint = null, rowHint = null))
        // Keep unaffected manual gaps. If a target is occupied, its tile reflows around the pinned drop.
        val occupiedTarget = overlap.minByOrNull { (geometry.bounds.getValue(it.tile.id).center - floating.center).getDistance() }
        val targetRow = occupiedTarget?.row ?: row
        val targetColumn = occupiedTarget?.column?.coerceAtMost(4 - dragged.columns) ?: column
        val before = marks.firstOrNull { it.row >= targetRow }?.tile?.id
        return placeFirstInSection(before, dragged.copy(columnHint = targetColumn, rowHint = targetRow - sectionStart(targetRow)))
    }

    private fun placeFirstInSection(before: String?, tile: ProfileTile): List<ProfileTile> {
        val result = tiles.filterNot { it.id == id }.map { anchored.getValue(it.id) }.toMutableList()
        val end = before?.let { key -> result.indexOfFirst { it.id == key } }?.takeIf { it >= 0 } ?: result.size
        val start = (end - 1 downTo 0).firstOrNull { result[it].kind.isSectionRow }?.plus(1) ?: 0
        result.add(start, tile)
        // Save the settled coordinates and visual reading order, including any tiles displaced by the drop.
        var startRow = 0
        return packProfileTiles(result).sortedWith(compareBy({ it.row }, { it.column })).map { position ->
            if (position.tile.kind.isSectionRow) {
                startRow = position.row + 1
                position.tile
            } else position.tile.copy(columnHint = position.column, rowHint = position.row - startRow)
        }
    }

    private fun reordered(target: String, tile: ProfileTile): List<ProfileTile> {
        val targetIndex = tiles.indexOfFirst { it.id == target }
        if (targetIndex < 0) return tiles
        return tiles.filterNot { it.id == id }.toMutableList().apply { add(targetIndex.coerceAtMost(size), tile) }
    }
}
