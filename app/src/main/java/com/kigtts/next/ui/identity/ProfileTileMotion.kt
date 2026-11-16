package com.kigtts.next.ui.identity

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.*
import com.kigtts.next.identity.*

internal enum class TileGesture { MOVE, RESIZE }
internal class ProfileTileMotion(initial: Rect) {
    private val animation = Animatable(initial, Rect.VectorConverter)
    private var direct by mutableStateOf<Rect?>(null)
    var gesture by mutableStateOf<TileGesture?>(null); private set
    var raised by mutableStateOf(false); private set
    val bounds: Rect get() = direct ?: animation.value
    fun begin(kind: TileGesture): Rect = bounds.also { direct = it; gesture = kind; raised = true }
    fun follow(bounds: Rect) { direct = bounds }
    fun release() { gesture = null }
    suspend fun settle(target: Rect) {
        if (gesture != null) { animation.stop(); return }
        direct?.let { animation.snapTo(it); direct = null }
        animation.animateTo(target, tween(220, easing = FastOutSlowInEasing))
        raised = false
    }
}

internal data class ProfileBoardGeometry(val bounds: Map<String, Rect>, val unit: Float, val gap: Float,
    val width: Float, val height: Float, val rowTops: FloatArray, val titleHeight: Float) {
    val pitch get() = unit + gap
}
internal fun profileBoardGeometry(tiles: List<ProfileTile>, width: Float, gap: Float, titleHeight: Float): ProfileBoardGeometry {
    val unit = ((width - gap * 3) / 4).coerceAtLeast(1f)
    val places = packProfileTiles(tiles)
    val rows = places.maxOfOrNull { it.row + it.tile.rows } ?: 0
    val tops = FloatArray(rows + 1)
    fun height(kind: ProfileTileKind) = when (kind) {
        ProfileTileKind.TITLE -> titleHeight
        ProfileTileKind.DIVIDER -> unit / 2f
        else -> unit
    }
    for (row in 0 until rows) {
        val section = places.firstOrNull { it.row == row && it.tile.kind.isSectionRow }
        tops[row + 1] = tops[row] + gap + (section?.let { height(it.tile.kind) } ?: unit)
    }
    val bounds = places.associate { place ->
        val tile = place.tile
        val left = (unit + gap) * place.column; val top = tops[place.row]
        tile.id to Rect(left, top, left + unit * tile.columns + gap * (tile.columns - 1),
            top + if (tile.kind.isSectionRow) height(tile.kind) else unit * tile.rows + gap * (tile.rows - 1))
    }
    return ProfileBoardGeometry(bounds, unit, gap, width, (tops.last() - gap).coerceAtLeast(0f), tops, titleHeight)
}
