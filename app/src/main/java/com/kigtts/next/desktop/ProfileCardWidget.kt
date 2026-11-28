package com.kigtts.next.desktop

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kigtts.next.identity.*
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.manager.HostPluginRuntime
import com.kigtts.next.media.MediaBitmapDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ProfileCardWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val revision = DesktopUpdates.revision.value
        val data = withContext(Dispatchers.IO) { ProfileWidgetData.load(context) }
        provideContent {
            ProfileWidgetContent(desktopWidgetData(data, revision) { ProfileWidgetData.load(context) })
        }
    }
}

internal fun desktopProfileQr(card: IdentityCard): ProfileTile? =
    card.tiles.firstOrNull { it.kind == ProfileTileKind.QR && validQrText(it.text) }
        ?: card.contacts.firstOrNull { validQrText(contactPayload(it)) }?.let {
            ProfileTile(kind = ProfileTileKind.QR, title = it.label, text = contactPayload(it))
        }

private fun validQrText(text: String) = text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= PROFILE_QR_MAX_BYTES

private data class ProfileWidgetData(
    val appearance: DesktopAppearance,
    val card: IdentityCard?,
    val cover: Bitmap?,
    val avatar: Bitmap?,
    val ink: Color,
    val labels: Map<DesktopEntry, String>,
    val createLabel: String,
    val defaultName: String,
) {
    companion object {
        suspend fun load(context: Context): ProfileWidgetData {
            val appearance = DesktopAppearance.load(context)
            val identity = IdentityRepository.storedCard(context)
            val labels = listOf(DesktopEntry.PROFILE, DesktopEntry.PROFILE_QR, DesktopEntry.PROFILE_SHARE)
                .associateWith { context.uiCopy(it.label) }
            var cover: Bitmap? = null
            var avatar: Bitmap? = null
            var ink = appearance.colors.onPrimaryContainer
            if (identity != null) {
                try {
                    val stickers = HostPluginRuntime.get(context.applicationContext as Application).stickers
                    val design = stickers.identityDesigns.get(identity.designId)
                    if (design != null) {
                        val background = stickers.identityBackgrounds.load(design, identity, 228)
                        cover = renderIdentityCover(identity, stickers, background, 228).also {
                            drawIdentityContacts(Canvas(it), identity, stickers, it.height.toFloat())
                        }
                        ink = Color(identityInk(background))
                    }
                    avatar = identity.avatar?.let(stickers::imageFile)?.let { MediaBitmapDecoder.decode(it, 132) }
                        ?.let { source ->
                            try { desktopAvatar(source, identity.avatarFrame) }
                            finally { source.recycle() }
                        }
                } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("KigttsDesktop", "Could not render the profile widget artwork", failure)
                }
            }
            return ProfileWidgetData(appearance, identity, cover, avatar, ink, labels,
                context.uiCopy("profile.create"), context.uiCopy("profile.my_card"))
        }
    }
}

private fun desktopAvatar(source: Bitmap, shape: String): Bitmap {
    val edge = 100f
    val scale = edge / minOf(source.width, source.height)
    val width = source.width * scale
    val height = source.height * scale
    return Bitmap.createBitmap(edge.toInt(), edge.toInt(), Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        val path = PathParser.createPathFromPathData(AvatarFrames.svgPath(shape))
        path.transform(Matrix().apply { setScale(edge, edge) })
        canvas.clipPath(path)
        val left = (edge - width) / 2f
        val top = (edge - height) / 2f
        canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

@Composable
private fun ProfileWidgetContent(data: ProfileWidgetData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val width = size.width.value
    val height = size.height.value
    val colors = data.appearance.colors
    val corner = (data.appearance.corner + 8f).coerceAtMost(26f)
    val corners = List(4) { corner }
    val open = actionStartActivity(DesktopEntry.PROFILE.intent(context))
    val card = data.card
    val root = GlanceModifier.fillMaxSize().desktopSurface(width, height, colors.surfaceContainer, corners)
    if (card == null) {
        Column(root.clickable(open), verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            DesktopGlyph(DesktopEntry.PROFILE, colors.primary, 40)
            Spacer(GlanceModifier.height(10.dp))
            Text(data.createLabel, style = TextStyle(color = ColorProvider(colors.onSurface), fontSize = 15.sp), maxLines = 2)
        }
        return
    }
    val bannerHeight = minOf((width - 12f) * 9f / 16f, height - 53f).coerceAtLeast(28f)
    val bannerWidth = minOf(width - 12f, bannerHeight * 16f / 9f).coerceAtLeast(28f)
    Column(root.padding(6.dp), horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically) {
        Box(GlanceModifier.width(bannerWidth.dp).height(bannerHeight.dp).clickable(open)
            .desktopSurface(bannerWidth, bannerHeight, colors.primaryContainer, corners)) {
            data.cover?.let { cover ->
                Image(ImageProvider(cover), null, GlanceModifier.fillMaxSize(), ContentScale.FillBounds)
            }
            Column(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.Vertical.Bottom) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    if (data.avatar != null) Image(ImageProvider(data.avatar), null, GlanceModifier.size(36.dp))
                    else DesktopGlyph(DesktopEntry.PROFILE, data.ink, 36)
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(card.nickname.ifBlank { data.defaultName },
                            style = TextStyle(color = ColorProvider(data.ink), fontSize = 19.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        if (card.bio.isNotBlank()) Text(card.bio, style = TextStyle(color = ColorProvider(data.ink), fontSize = 11.sp), maxLines = 2)
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(5.dp))
        val canShowQr = desktopProfileQr(card) != null
        val buttonWidth = (width - 12f - if (canShowQr) 4f else 0f) / if (canShowQr) 2f else 1f
        Row(GlanceModifier.fillMaxWidth()) {
            if (canShowQr) {
                ProfileWidgetButton(DesktopEntry.PROFILE_QR, data, GlanceModifier.defaultWeight(), buttonWidth,
                    colors.secondaryContainer, colors.onSecondaryContainer, listOf(outerButtonCorner(corner), 6f, 6f, outerButtonCorner(corner)))
                Spacer(GlanceModifier.width(4.dp))
            }
            ProfileWidgetButton(DesktopEntry.PROFILE_SHARE, data, GlanceModifier.defaultWeight(), buttonWidth,
                colors.primaryContainer, colors.onPrimaryContainer,
                if (canShowQr) listOf(6f, outerButtonCorner(corner), outerButtonCorner(corner), 6f) else corners)
        }
    }
}

private fun outerButtonCorner(corner: Float) = corner.coerceAtMost(19f)

@Composable
private fun ProfileWidgetButton(entry: DesktopEntry, data: ProfileWidgetData, modifier: GlanceModifier,
    width: Float, fill: Color, ink: Color, corners: List<Float>) {
    val context = LocalContext.current
    Row(modifier.height(36.dp).desktopSurface(width, 36f, fill, corners)
        .clickable(actionStartActivity(entry.intent(context))), horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically) {
        DesktopGlyph(entry, ink, 17)
        Spacer(GlanceModifier.width(5.dp))
        Text(data.labels.getValue(entry), style = TextStyle(color = ColorProvider(ink), fontSize = 11.sp), maxLines = 1)
    }
}
