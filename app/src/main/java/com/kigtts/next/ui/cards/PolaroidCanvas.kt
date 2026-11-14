package com.kigtts.next.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.R
import com.kigtts.next.cards.polaroidBranding
import com.kigtts.plugin.api.*
import java.io.File
import kotlin.math.roundToInt

private class CardGestureLayer { var layer: CardLayer? = null; var handleActive = false }

/** Preview and export resolve the same SDK paper/photo geometry; layers use normalized paper coordinates. */
@Composable
internal fun PolaroidCanvas(card: PolaroidCard, imageFile: (String) -> File?, modifier: Modifier = Modifier,
    selectedLayer: String? = null, editable: Boolean = false, selectLayer: (String?) -> Unit = {},
    updateLayer: (CardLayer) -> Unit = {}, photoEditable: Boolean = false,
    onPhotoChange: (PolaroidCard) -> Unit = {}, onPhotoEnd: () -> Unit = {}, frame: PolaroidFrameStyle? = null,
    decodeMaximum: Int = 2048, photoOverride: ImageBitmap? = null, paperElevation: androidx.compose.ui.unit.Dp? = null,
    removeLayer: (String) -> Unit = {}, configureLayer: ((CardLayer) -> Unit)? = null, configurableLayer: (CardLayer) -> Boolean = { false },
    backgroundDecoration: @Composable () -> Unit = {}, photoContent: (@Composable () -> Unit)? = null,
    viewScale: Float = 1f, photoFacts: com.kigtts.next.cards.FrozenPhotoFacts? = null) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var transforming by remember { mutableStateOf(false) }
    val current = rememberUpdatedState(card)
    val selectedId = rememberUpdatedState(selectedLayer)
    val change = rememberUpdatedState(updateLayer)
    val remove = rememberUpdatedState(removeLayer)
    val select = rememberUpdatedState(selectLayer)
    val geometry = com.kigtts.next.cards.polaroidPaperGeometry(card, frame)
    val branding = polaroidBranding(card, frame)
    val captionWidth = branding?.captionWidth ?: geometry.titleWidth
    val gesture = remember(card.id) { CardGestureLayer() }
    val paper = Color(polaroidPaperColor(card, frame))
    val darkPaper = paper.luminance() < .45f
    val ink = if (darkPaper) Color.White else Color(0xFF202020)
    val captionInk = if (darkPaper) Color(0xFFDDDDDD) else Color(0xFF4A4A4A)
    val haptics = rememberUpdatedState(LocalHapticFeedback.current)
    val elevation by animateDpAsState(if (transforming) 10.dp else 3.dp, label = "polaroid-elevation")
    Surface(modifier.sharedPolaroidPhoto(card.id).aspectRatio(geometry.aspectRatio.toFloat()).onSizeChanged { size = it }.testTag("polaroid/" + card.id),
        shape = RoundedCornerShape(if (com.kigtts.next.cards.PhotoFrameSpec.from(card.frameStyleId) != null) 0.dp else 6.dp), color = paper, shadowElevation = paperElevation ?: elevation) {
        BoxWithConstraints(Modifier.fillMaxSize().then(if (editable && selectedLayer != null) Modifier.pointerInput(card.id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val watching = !gesture.handleActive
                gesture.layer = if (watching) current.value.layers.firstOrNull { it.id == selectedId.value } else null
                transforming = watching
                if (watching) haptics.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                val original = gesture.layer
                var completed = false
                try { do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed }); completed = true }
                finally {
                    val moved = gesture.layer
                    if (watching && moved != null && (moved.x !in 0.0..1.0 || moved.y !in 0.0..1.0)) {
                        if (completed) { remove.value(moved.id); select.value(null); haptics.value.performHapticFeedback(HapticFeedbackType.GestureEnd) }
                        else original?.let { change.value(it) }
                    }
                    transforming = false; gesture.layer = null
                }
            }
        }.pointerInput(card.id) {
            detectTransformGestures { _, pan, zoom, rotation ->
                val layer = gesture.layer
                if (layer != null) {
                    gesture.layer = transformCardLayer(layer, pan.x, pan.y, zoom, rotation, size.width.toFloat(), size.height.toFloat(), allowOutside = true)
                    change.value(requireNotNull(gesture.layer))
                }
            }
        } else Modifier).then(if (editable) Modifier.pointerInput(card.id) {
            // A child sticker consumes its tap; the remaining paper, caption and margins cancel selection.
            detectTapGestures(onTap = { select.value(null) })
        } else Modifier)) {
            val width = maxWidth
            fun paperLength(units: Double) = width * (units / geometry.paperWidth).toFloat()
            val density = LocalDensity.current
            val canvasWidthPx = with(density) { maxWidth.toPx() }
            val canvasHeightPx = with(density) { maxHeight.toPx() }
            val watermarkStyle = com.kigtts.next.cards.PhotoFrameSpec.from(card.frameStyleId)?.watermark
            val frameGlassEnabled = watermarkStyle?.palette == com.kigtts.next.cards.WatermarkPalette.GLASS
            val source = card.imageId?.takeIf { photoContent == null || frameGlassEnabled }?.let {
                val loaded = rememberCardImage(imageFile(it), cardPhotoDecodeSize(
                    canvasWidthPx * (geometry.photoWidth / geometry.paperWidth).toFloat(),
                    canvasWidthPx * (geometry.photoHeight / geometry.paperWidth).toFloat(), card.photoZoom, viewScale, decodeMaximum))
                photoOverride?.takeIf { warm -> loaded == null || maxOf(warm.width, warm.height) > maxOf(loaded.width, loaded.height) } ?: loaded
            }
            val image = rememberCardPhotoStyle(source, card.imageId.orEmpty(), card.photoStyle, imageFile)
            Box(Modifier.offset(x = paperLength(geometry.photoLeft), y = paperLength(geometry.photoTop))
                .width(paperLength(geometry.photoWidth)).height(paperLength(geometry.photoHeight)).clip(RoundedCornerShape(width * (frame?.photoCornerRadius ?: 0.0).toFloat()))
                .then(if (editable) Modifier.clickable(onClick = feedbackClick { select.value(null) }) else Modifier),
                contentAlignment = Alignment.Center) {
                if (photoContent != null || image != null || !com.kigtts.next.cards.isFullPhotoPaper(card)) CardPhotoBackground(card, image, photoEditable && selectedLayer == null, onPhotoChange, onPhotoEnd, Modifier.fillMaxSize(), photoContent)
            }
            backgroundDecoration()
            watermarkStyle?.let { watermark ->
                val glass = rememberPhotoFrameGlass(image, watermark, geometry, card)
                val context = androidx.compose.ui.platform.LocalContext.current
                val sourceId = card.originalImageId ?: card.imageId
                val facts by produceState(com.kigtts.next.cards.WatermarkFacts(), sourceId, photoFacts) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val file = sourceId?.let(imageFile)
                        com.kigtts.next.cards.watermarkFacts(file, photoFacts ?: com.kigtts.next.cards.PolaroidPhotoFacts.read(file, false))
                    }
                }
                androidx.compose.foundation.Canvas(Modifier.matchParentSize().testTag("camera-watermark-paper")) {
                    drawIntoCanvas { com.kigtts.next.cards.drawCameraWatermark(context, it.nativeCanvas, watermark, geometry, facts, this.size.width, this.size.height, glass) }
                }
            }
            if (editable && current.value.layers.any { it.id == selectedId.value && (it.x !in 0.0..1.0 || it.y !in 0.0..1.0) })
                Text("松手删除贴纸", Modifier.align(Alignment.TopCenter).padding(12.dp), color = MaterialTheme.colorScheme.error)
            if (frame?.borderColor != null && frame.borderWidth > 0.0) Box(Modifier
                .offset(x = paperLength(geometry.photoLeft), y = paperLength(geometry.photoTop))
                .width(paperLength(geometry.photoWidth)).height(paperLength(geometry.photoHeight))
                .border(width * frame.borderWidth.toFloat(), Color(requireNotNull(frame.borderColor)),
                    RoundedCornerShape(width * frame.photoCornerRadius.toFloat())))
            frame?.overlayImageId?.let { imageId ->
                rememberCardImage(imageFile(imageId), cardImageDecodeSize(maxOf(canvasWidthPx, canvasHeightPx) * viewScale, decodeMaximum))?.let { overlay ->
                    Image(overlay, null, Modifier.fillMaxSize().drawWithContent {
                        if (com.kigtts.next.cards.PhotoFrameSpec.from(card.frameStyleId) != null) {
                            val scale = size.width / geometry.paperWidth.toFloat()
                            clipRect((geometry.photoLeft * scale).toFloat(), (geometry.photoTop * scale).toFloat(),
                                (geometry.photoRight * scale).toFloat(), (geometry.photoBottom * scale).toFloat(), ClipOp.Difference) { this@drawWithContent.drawContent() }
                        } else drawContent()
                    }, contentScale = ContentScale.FillBounds)
                }
            }
            if (card.layers.any { it.kind == CardLayerKind.PHOTO_INFO }) {
                val caption = remember(card, geometry, captionWidth, canvasWidthPx, ink, captionInk) {
                    com.kigtts.next.cards.polaroidCaption(card, geometry, captionWidth, canvasWidthPx, ink.toArgb(), captionInk.toArgb())
                }
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().semantics { contentDescription = caption.description }.testTag("polaroid-photo-information")) {
                    drawIntoCanvas { caption.draw(it.nativeCanvas) }
                }
            } else {
            Text(card.title, Modifier.offset(x = paperLength(geometry.titleX), y = paperLength(geometry.titleY))
                .width(paperLength(captionWidth)), color = ink, maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = with(density) { paperLength(geometry.titleSize).toSp() }, lineHeight = with(density) { paperLength(geometry.titleSize * 1.2).toSp() })
            if (card.description.isNotBlank()) Text(card.description,
                Modifier.offset(x = paperLength(geometry.titleX), y = paperLength(geometry.descriptionY)).width(paperLength(captionWidth)),
                color = captionInk, maxLines = 3,
                style = MaterialTheme.typography.bodySmall.copy(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                fontSize = with(density) { paperLength(geometry.descriptionSize).toSp() }, lineHeight = with(density) { paperLength(geometry.descriptionSize * 1.2).toSp() })
            }
            branding?.let { logo ->
                Icon(painterResource(R.drawable.logo_horizontal), "KIGTTS",
                    Modifier.offset(x = paperLength(logo.x), y = paperLength(logo.y))
                        .width(paperLength(logo.width)).height(paperLength(logo.height)).testTag("polaroid-brand-logo"), tint = ink)
            }
            card.layers.filter { it.kind != CardLayerKind.PHOTO_INFO }.forEach { layer -> key(layer.id) {
                val density = LocalDensity.current
                var textControlWidth by remember(layer.id) { mutableStateOf(72.dp) }
                val layerWidth = width * layer.width.toFloat()
                val isSelected = editable && selectedLayer == layer.id
                val image = layer.imageId?.let { rememberCardImage(imageFile(it), cardImageDecodeSize((canvasWidthPx * layer.width).toFloat() * viewScale, minOf(2048, decodeMaximum))) }
                val mirrored = Modifier.graphicsLayer { scaleX = if (layer.flipX) -1f else 1f; scaleY = if (layer.flipY) -1f else 1f }
                Box(Modifier.align(Alignment.Center).offset {
                    IntOffset(((layer.x - .5) * canvasWidthPx).roundToInt(),
                        ((layer.y - .5) * canvasHeightPx).roundToInt())
                }.then(if (isSelected) Modifier.sizeIn(minWidth = 72.dp, minHeight = 72.dp) else Modifier)
                    .graphicsLayer { rotationZ = layer.rotation.toFloat() }
                    .then(if (isSelected && layer.kind == CardLayerKind.IMAGE) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
                    .then(if (editable) Modifier.clickable(onClick = feedbackClick { if (isSelected && configureLayer != null) configureLayer(layer) else select.value(layer.id) }) else Modifier)
                    .testTag("card-layer/" + layer.id).semantics {
                        if (editable) { selected = isSelected; contentDescription = if (layer.kind == CardLayerKind.TEXT) "文字：${layer.text}" else "贴纸" }
                    }, contentAlignment = Alignment.Center) {
                    if (layer.kind == CardLayerKind.IMAGE) {
                        val aspect = if (image != null) image.width.toFloat() / image.height.coerceAtLeast(1) else 1f
                        if (image != null) Image(image, null, Modifier.requiredWidth(layerWidth).aspectRatio(aspect).then(mirrored), contentScale = ContentScale.Fit)
                        else Icon(Icons.Default.Image, null, Modifier.size(layerWidth), tint = ink)
                    } else Text(layer.text, Modifier.cardLayerTextBounds(width, textControlWidth).then(mirrored), color = Color(layer.color),
                        style = LocalTextStyle.current.copy(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = with(density) { layerWidth.toSp() }, lineHeight = with(density) { (layerWidth * 1.2f).toSp() },
                        onTextLayout = { layout ->
                            val visible = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
                            textControlWidth = maxOf(72.dp, with(density) { visible.toDp() })
                        })
                    if (isSelected) {
                        if (layer.kind == CardLayerKind.TEXT) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(textControlWidth).fillMaxHeight()
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))) {
                                CardLayerHandles(layer, updateLayer, { removeLayer(layer.id) }, configureLayer?.takeIf { configurableLayer(layer) }?.let { configure -> { configure(layer) } }) { gesture.handleActive = it }
                            }
                        } else CardLayerHandles(layer, updateLayer, { removeLayer(layer.id) }, configureLayer?.takeIf { configurableLayer(layer) }?.let { configure -> { configure(layer) } }) { gesture.handleActive = it }
                    }
                }
            } }
        }
    }
}
