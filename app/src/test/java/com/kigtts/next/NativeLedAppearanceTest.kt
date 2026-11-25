package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.unit.IntSize
import com.kigtts.next.state.AppState
import com.kigtts.next.state.HomePanel
import com.kigtts.next.state.HomePanelSizes
import com.kigtts.next.state.LedPlayback
import com.kigtts.next.state.MediaQuickTextCollapse
import com.kigtts.next.state.afterOpeningMedia
import com.kigtts.next.theme.ContrastLevel
import com.kigtts.next.theme.ResolvedAppearance
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.media.MediaSafeArea
import com.kigtts.next.ui.media.LedInkMask
import com.kigtts.next.ui.media.LedLampGrid
import com.kigtts.next.ui.media.ledLampPitch
import com.kigtts.next.ui.media.rasterLed
import com.kigtts.plugin.api.AppearanceBackground
import com.kigtts.plugin.api.AppearanceContrast
import com.kigtts.plugin.api.AppearanceMode
import com.kigtts.plugin.api.AppearancePatch
import com.kigtts.plugin.api.NativeSubtitleRenderer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeLedAppearanceTest {
    @Test fun partialAppearanceKeepsUserSeedAndBackgroundUnlessSpecified() {
        val base = ThemeSettings(seed = 0xffa02060, wallpaperEnabled = true, mode = ThemeMode.LIGHT, background = "sample")
        val led = ResolvedAppearance(AppearancePatch(mode = AppearanceMode.DARK, pureBlackDark = true, backgroundDim = 1f))
        assertEquals(base.copy(mode = ThemeMode.DARK, deeperDark = true, backgroundDim = 1f), led.applyTo(base))
        val saved = ResolvedAppearance(AppearancePatch(themeColor = 0xff404040, contrast = AppearanceContrast.HIGH,
            background = AppearanceBackground.Clear)).applyTo(base)
        assertFalse(saved.wallpaperEnabled)
        assertEquals(0xff404040L, saved.seed)
        assertEquals(ContrastLevel.HIGH, saved.contrast)
        assertNull(saved.background)
    }

    @Test fun autoCollapseOnlyChangesTheVisiblePanel() {
        val initial = AppState(panel = HomePanel.QUICK_TEXT, panelHeight = 364f, lastExpandedPanelHeight = 364f, panelColumns = 3)
        assertSame(initial, initial.afterOpeningMedia())
        val row = initial.copy(mediaQuickTextCollapse = MediaQuickTextCollapse.SINGLE_ROW).afterOpeningMedia()
        assertEquals(HomePanelSizes.PEEK, row.panelHeight)
        assertTrue(row.mediaSingleRow)
        assertEquals(initial.lastExpandedPanelHeight, row.lastExpandedPanelHeight)
        assertEquals(initial.panelColumns, row.panelColumns)
        val closed = initial.copy(mediaQuickTextCollapse = MediaQuickTextCollapse.COLLAPSE).afterOpeningMedia()
        assertNull(closed.panel)
        assertEquals(initial.lastExpandedPanelHeight, closed.lastExpandedPanelHeight)
    }

    @Test fun longLedCaptionHasLitPixelsAndClipsToSafeArea() = runBlocking {
        val board = IntSize(300, 160)
        val scene = requireNotNull(rasterLed("Hello LED ".repeat(12), board, MediaSafeArea(12, 16, 12, 16),
            23f, 1f, NativeSubtitleRenderer.Led(), Typeface.DEFAULT_BOLD))
        assertTrue(scene.period(96f) > 0f)
        val target = Bitmap.createBitmap(board.width, board.height, Bitmap.Config.ARGB_8888)
        scene.draw(Canvas(target), 0f, 96f)
        val pixels = IntArray(board.width * board.height)
        target.getPixels(pixels, 0, board.width, 0, 0, board.width, board.height)
        assertTrue(pixels.count { (it and 0xff) > 120 } > 20)
        assertTrue(pixels.take(board.width * 12).none { (it and 0xff) > 120 })
    }

    @Test fun movingAndScalingTheGlyphDoesNotShiftLampCenters() {
        val board = IntSize(168, 112)
        val ink = LedInkMask(256, 256)
        ink.copyTile(IntArray(256 * 256) { -1 }, 0, 0, 256, 256)
        val grid = LedLampGrid(board, 7f, NativeSubtitleRenderer.Led(), ink)
        val viewport = RectF(0f, 0f, board.width.toFloat(), board.height.toFloat())
        val target = Bitmap.createBitmap(board.width, board.height, Bitmap.Config.ARGB_8888)
        fun draw(x: Float, y: Float, unit: Float): IntArray {
            target.eraseColor(0xff000000.toInt())
            val canvas = Canvas(target)
            grid.drawUnlit(canvas)
            grid.drawLit(canvas, viewport, x, y, unit, Float.NaN)
            return IntArray(board.width * board.height).also { target.getPixels(it, 0, board.width, 0, 0, board.width, board.height) }
        }
        val original = draw(-22f, -15f, 1f)
        assertTrue(original.distinct().size > 2)
        assertArrayEquals(original, draw(-24.25f, -17.5f, 1.4f))
    }

    @Test fun lampRowsFollowTheHeightEvenWhenTheViewportWidthChanges() {
        assertEquals(90f, ledLampPitch(IntSize(600, 720), 3f, 720f, 8), .001f)
        assertEquals(90f, ledLampPitch(IntSize(1200, 720), 3f, 720f, 8), .001f)
        assertEquals(45f, ledLampPitch(IntSize(600, 360), 3f, 360f, 8), .001f)
        assertEquals(45f, ledLampPitch(IntSize(600, 720), 3f, 720f, 16), .001f)
    }

    @Test fun panelResizeReusesGlyphsAndDrawsToTheNewBounds() = runBlocking {
        val style = NativeSubtitleRenderer.Led(background = 0xff132333, displayHeight = 1f, dotRows = 8)
        val raster = requireNotNull(rasterLed("LED", IntSize(150, 100), MediaSafeArea(),
            30f, 1f, style, Typeface.DEFAULT_BOLD))
        val expanded = IntSize(150, 240)
        val target = Bitmap.createBitmap(expanded.width, expanded.height, Bitmap.Config.ARGB_8888)
        raster.draw(Canvas(target), 0f, 96f, bounds = expanded)
        assertEquals(style.background.toInt(), target.getPixel(0, 239))
        assertNotEquals(style.background.toInt(), target.getPixel(15, 225))
    }

    @Test fun aPartiallyCoveredCellStillUsesTheFullLampColor() {
        val board = IntSize(80, 80)
        val ink = LedInkMask(80, 80)
        ink.copyTile(IntArray(80 * 80) { 0x70ffffff }, 0, 0, 80, 80)
        val color = 0xffa9eff2
        val grid = LedLampGrid(board, 20f, NativeSubtitleRenderer.Led(foreground = color, glow = 0f), ink)
        val viewport = RectF(0f, 0f, 80f, 80f)
        val target = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        grid.drawLit(Canvas(target), viewport, 0f, 0f, 1f, Float.NaN)
        assertEquals(color.toInt(), target.getPixel(30, 30))
    }

    @Test fun swipeCoastsThenResumesAutomaticScroll() {
        val playback = LedPlayback()
        playback.layout(800f, loop = true, right = false)
        assertTrue(playback.startScrub())
        playback.scrub(-100f)
        assertEquals(100f, playback.offset, .01f)
        playback.tick(.1f, 40f)
        assertEquals(100f, playback.offset, .01f)
        playback.finishScrub(-700f)
        playback.tick(.05f, 40f)
        val first = playback.offset
        assertTrue(first > 100f)
        repeat(36) { playback.tick(.05f, 40f) }
        val settled = playback.offset
        assertTrue(settled > first)
        playback.tick(.25f, 40f)
        assertTrue(playback.offset > settled)
    }
}
