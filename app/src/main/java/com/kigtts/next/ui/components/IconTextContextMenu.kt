@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.contextmenu.data.*
import androidx.compose.foundation.text.contextmenu.provider.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.kigtts.next.text.TextFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

internal class IconTextMenuSession(val data: TextContextMenuDataProvider, private val onClose:(IconTextMenuSession)->Unit) : TextContextMenuSession {
    val closed = CompletableDeferred<Unit>()
    val owner=data.data().components.firstNotNullOfOrNull { it.key as? TextMenuAnchor }
    var visible by mutableStateOf(true)
    override fun close() { if(visible) { owner?.let { it.dismissed=true;it.dismiss() };finish() } }
    fun finish() { visible=false;if(closed.complete(Unit))onClose(this) }
}

internal class IconTextMenuProvider : TextContextMenuProvider {
    private val mutex = MutatorMutex()
    var session by mutableStateOf<IconTextMenuSession?>(null)
        private set
    var displayed by mutableStateOf<IconTextMenuSession?>(null)
        private set
    fun clearVisual(current:IconTextMenuSession) { if(displayed===current)displayed=null }
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) = mutex.mutate {
        val current = IconTextMenuSession(dataProvider) { if(session===it)session=null }
        // Legacy Foundation can request the same toolbar again after relayout, even after close.
        // Wait for its gesture/request cancellation without reopening a dismissed selection.
        // A fresh touch on the owning field resets this guard before Foundation starts selection.
        if(current.owner?.dismissed==true)awaitCancellation()
        try { session = current;displayed=current;current.closed.await() }
        finally { current.finish();if (session === current) session = null }
    }
}

/** Public Foundation menu callbacks keep native copy/paste/selection behavior and accessibility. */
@Composable
internal fun ProvideIconTextContextMenu(content: @Composable () -> Unit) {
    val provider = remember { IconTextMenuProvider() }
    val focus=LocalFocusManager.current
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    DisposableEffect(provider) { onDispose { provider.session?.finish() } }
    CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides provider,
        LocalTextContextMenuDropdownProvider provides provider) {
        Box(Modifier.onGloballyPositioned { coordinates = it }, propagateMinConstraints = true) {
            content()
            provider.displayed?.let { session -> coordinates?.takeIf { it.isAttached }?.let { anchor ->
                key(session) { IconTextMenuPopup(session,anchor,{ provider.clearVisual(session) }) {
                    if(session.owner==null)focus.clearFocus();session.close()
                } }
            } }
        }
    }
    BackHandler(provider.displayed != null) {
        provider.session?.let { if(it.owner==null)focus.clearFocus();it.close() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconTextMenuPopup(session: IconTextMenuSession, anchor: LayoutCoordinates,finished:()->Unit,dismiss:()->Unit) {
    val density = LocalDensity.current
    val reveal=remember { Animatable(0f) }
    LaunchedEffect(session.visible) {
        reveal.animateTo(if(session.visible)1f else 0f,tween(if(session.visible)160 else 120))
        if(!session.visible)finished()
    }
    LaunchedEffect(session.owner?.focused) { if(session.owner?.focused==false)session.finish() }
    // Compute during popup placement, after the text field's latest layout. Retain only for exit.
    val lastBounds=remember { arrayOf(Rect.Zero) }
    fun bounds():Rect {
        if(session.visible&&anchor.isAttached) {
            val selected=session.data.contentBounds(anchor)
            val field=session.owner?.coordinates?.takeIf { it.isAttached }?.let { anchor.localBoundingBoxOf(it) }
            lastBounds[0]=if(field==null)selected else Rect(selected.left.coerceIn(field.left,field.right),
                selected.top.coerceIn(field.top,field.bottom),selected.right.coerceIn(field.left,field.right),
                selected.bottom.coerceIn(field.top,field.bottom))
        }
        return lastBounds[0]
    }
    val safe = WindowInsets.safeDrawing
    val bottom = maxOf(safe.getBottom(density), WindowInsets.ime.getBottom(density))
    val margin = with(density) { 8.dp.roundToPx() }
    val shadowInset = 18.dp
    val menuShape = MaterialTheme.shapes.medium
    val menuShadow = remember { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = .2f), offset = DpOffset(0.dp, 3.dp)) }
    val shadowPixels = with(density) { shadowInset.roundToPx() }
    val top = safe.getTop(density)
    val left = safe.getLeft(density,LocalLayoutDirection.current)
    val right = safe.getRight(density,LocalLayoutDirection.current)
    val availableWidth=with(density) { (LocalWindowInfo.current.containerSize.width-left-right-margin*2-shadowPixels*2).coerceAtLeast(1).toDp() }
    val lastItems=remember { arrayOf<List<TextContextMenuItem>>(emptyList()) }
    val components=if(session.visible)session.data.data().components.filterIsInstance<TextContextMenuItem>()
        .filter { it.key !is TextMenuAnchor&&!isReadAloudItem(it) }.also { lastItems[0]=it } else lastItems[0]
    // A field can move while the provider's full-window root stays still (scroll/app bar).
    // Reposition the native popup on actual geometry changes, not on every animation draw.
    val position = remember(session.owner?.windowBounds,top,bottom,margin,shadowPixels,left,right) {
        TextMenuPosition(::bounds,top,bottom,margin,shadowPixels,left,right)
    }
    Popup(position, onDismissRequest = dismiss, properties = PopupProperties(focusable = false)) {
        // Both resize clipping and the alpha offscreen layer must include the shadow, not just the surface.
        Box(Modifier.animateContentSize(tween(140)).graphicsLayer { alpha=reveal.value }
            .testTag("text-icon-menu-window").pointerInput(session,shadowPixels) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                    if(down.position.x<shadowPixels||down.position.x>size.width-shadowPixels||
                        down.position.y<shadowPixels||down.position.y>size.height-shadowPixels) {
                        dismiss();down.consume()
                    }
                }
            }.padding(shadowInset)) {
        // Draw a cached native blur rather than an elevation-only RenderNode: popup fallback canvases
        // also retain the real shadow, and the padded parent owns its clipping/opacity during transitions.
        Surface(shape = menuShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.graphicsLayer { scaleX=.92f+.08f*reveal.value;scaleY=.8f+.2f*reveal.value }
                .dropShadow(menuShape, menuShadow).testTag("text-icon-menu")) {
            FlowRow(Modifier.widthIn(max = availableWidth).padding(6.dp)
                .graphicsLayer { scaleX=1f/(.92f+.08f*reveal.value);scaleY=1f/(.8f+.2f*reveal.value) },
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                components.forEachIndexed { index, item ->
                    key(item.key) {
                    val action = textMenuAction(item.key, index)
                    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(item.label) } }, state = rememberTooltipState()) {
                        MotionIconButton(feedbackClick { if(session.visible)item.onClick(session) }, Modifier.size(44.dp).testTag("text-menu-" + action.first)) {
                            Icon(action.second, item.label)
                        }
                    }
                    }
                }
            }
        }
        }
    }
}

internal fun isReadAloudItem(item:TextContextMenuItem)=item.key is ProcessTextKey &&
    item.label.trim().lowercase() in setOf("朗读","朗讀","read aloud","read out loud","speak")

private fun textMenuAction(key: Any, index: Int) = when (key) {
    TextContextMenuKeys.CutKey -> "cut" to Icons.Default.ContentCut
    TextContextMenuKeys.CopyKey -> "copy" to Icons.Default.ContentCopy
    TextContextMenuKeys.PasteKey -> "paste" to Icons.Default.ContentPaste
    TextContextMenuKeys.SelectAllKey -> "select-all" to Icons.Default.SelectAll
    TextContextMenuKeys.AutofillKey -> "autofill" to Icons.Default.AutoFixHigh
    TextFormat.BOLD -> "bold" to Icons.Default.FormatBold
    TextFormat.ITALIC -> "italic" to Icons.Default.FormatItalic
    TextFormat.UNDERLINE -> "underline" to Icons.Default.FormatUnderlined
    TextFormat.COLOR -> "color" to Icons.Default.FormatColorText
    TextFormat.SIZE -> "size" to Icons.Default.FormatSize
    else -> "extra-$index" to Icons.Default.MoreHoriz
}
