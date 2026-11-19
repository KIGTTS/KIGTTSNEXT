package com.kigtts.next.ui.home

import com.kigtts.next.i18n.rememberUiCopy

import com.kigtts.next.i18n.uiText
import com.kigtts.next.i18n.uiCopy

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import com.kigtts.next.media.*

@Composable
fun EditorToolbar(state: AppState, model: AppViewModel) {
    val copy = rememberUiCopy()
    var menu by remember { mutableStateOf(false) }
    var alignmentDialog by remember { mutableStateOf(false) }
    var fontBar by remember { mutableStateOf(false) }
    var listeningSettings by remember { mutableStateOf(false) }
    val context = model.context()
    val registeredStart = remember(context, state.pluginRevision) {
        cornerActions(UiSlot.EDITOR_TOP_START, model, listenSettings = { listeningSettings = true })
    }
    val registeredEnd = remember(context, state.pluginRevision) { cornerActions(UiSlot.EDITOR_TOP_END, model) }
    val start = listOf(ElasticAction("editor-menu", copy("subtitle.menu"), Icons.Default.MoreVert, { menu = true }),
        ElasticAction("editor-flip", uiText("subtitle.flip"), Icons.Default.SwapVert, model::flipSubtitle)) + registeredStart
    LaunchedEffect(state.mediaId) { if (state.mediaId != null) fontBar = false }
    val end = (if (state.mediaId == null) listOf(ElasticAction("editor-font-size", copy("subtitle.adjust_font"), Icons.Default.ZoomIn, { fontBar = !fontBar })) else emptyList()) + registeredEnd
    Box {
        Row(Modifier.fillMaxWidth()) {
            FontToolbarSwap(fontBar, copy("subtitle.font"), Modifier.weight(.68f)) { expanded ->
            if (expanded) InlineFontBar(copy("subtitle.font"), state.subtitleSizePercent, SubtitleSize.range, model::setSubtitleSizePercent,
                { fontBar = false }, Modifier.fillMaxWidth(),
                valueDescription = uiText("subtitle.size", "size" to "%.1f".format(state.subtitleSizePercent)))
            else ElasticButtonGroup(start, Modifier.fillMaxWidth(), overflowTag = "editor-top-start-more", overflowLabel = copy("subtitle.more_top_start"), stableItems = true)
            }
            Spacer(Modifier.weight(.02f))
            ElasticButtonGroup(end, Modifier.weight(.3f), alignEnd = true,
                overflowTag = "editor-top-end-more", overflowLabel = copy("subtitle.more_top_end"), stableItems = true)
        }
        androidx.activity.compose.BackHandler(menu) { menu = false }
        DropdownMenu(menu, { menu = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
            val media = model.mediaRepository.get(state.mediaId)
            if (state.mediaId == null || media?.kind == MediaKind.AUDIO) {
                DropdownMenuItem(text = { Text(uiText("quick.add_from_text")) },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    enabled = state.editorText.isNotBlank(),
                    onClick = feedbackClick { menu = false; model.addEditorToQuickText() },
                    modifier = Modifier.testTag("subtitle-add-quick-text"))
            }
            if (media?.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO, MediaKind.VIDEO)) MediaScale.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(copy("subtitle.media." + mode.name.lowercase(java.util.Locale.ROOT))) }, leadingIcon = { if (media?.scale == mode) Icon(Icons.Default.Check, null) },
                    onClick = feedbackClick { model.mediaScale(mode); menu = false }, modifier = Modifier.testTag("media-scale-" + mode.name.lowercase()))
            }
            val appearance = state.subtitleAppearance
            DropdownMenuItem(text = { Text(if (appearance.bold) copy("subtitle.bold_off") else copy("subtitle.bold_on")) },
                leadingIcon = { Icon(Icons.Default.FormatBold, null) },
                trailingIcon = { if (appearance.bold) Icon(Icons.Default.Check, null) },
                onClick = feedbackClick { model.setSubtitleAppearance(appearance.copy(bold = !appearance.bold)); menu = false },
                modifier = Modifier.testTag("subtitle-bold"))
            DropdownMenuItem(text = { Text(uiText("subtitle.align")) },
                leadingIcon = { Icon(MaterialSymbols.vector("segment") ?: Icons.Default.FormatAlignCenter, null) },
                trailingIcon = { Text(copy("subtitle.alignment." + appearance.alignment.name.lowercase(java.util.Locale.ROOT)), style = MaterialTheme.typography.labelMedium) },
                onClick = feedbackClick { menu = false; alignmentDialog = true }, modifier = Modifier.testTag("subtitle-alignment"))
            DropdownMenuItem(text = { Text(if (appearance.richTextEditing) copy("subtitle.rich_off") else copy("subtitle.rich_on")) },
                leadingIcon = { Icon(Icons.Default.FormatPaint, null) },
                trailingIcon = { if (appearance.richTextEditing) Icon(Icons.Default.Check, null) },
                onClick = feedbackClick { model.setSubtitleAppearance(appearance.copy(richTextEditing = !appearance.richTextEditing)); menu = false },
                modifier = Modifier.testTag("subtitle-rich-editing"))
            model.registry.actions(UiSlot.EDITOR_MENU, context).forEach { action ->
                DropdownMenuItem(text = { Text(action.action.label) }, leadingIcon = { Icon(action.action.icon.vector(), null) },
                    onClick = feedbackClick { model.invoke(action); menu = false })
            }
        }
        if (alignmentDialog) SubtitleAlignmentDialog(state.subtitleAppearance.alignment,
            { model.setSubtitleAppearance(model.state.value.subtitleAppearance.copy(alignment = it)) }, { alignmentDialog = false })
        if (listeningSettings) ListeningQuickSettings(model, state.captionTextSize) { listeningSettings = false }
    }
}

fun cornerActions(slot: UiSlot, model: AppViewModel, owner: String? = null, listenSettings: (() -> Unit)? = null) = model.registry.actions(slot, model.context())
    .filter { (owner == null || it.plugin.descriptor.id == owner) &&
        (it.plugin.descriptor.id != "service.speech-recognition" || model.captionServiceReady) }.map { registered ->
    val service = registered.plugin.descriptor.id
    val listen = service == "service.speech-recognition" && registered.action.id == "listen"
    val dismiss = service == "service.speech-recognition" && registered.action.id == "close" && slot == UiSlot.LISTENING_BOTTOM_END
    val state = model.state.value
    val label = when {
        listen && state.listening && !state.voiceInputActive ->
            model.getApplication<android.app.Application>().uiCopy("listening.stop", locale = state.uiLocale)
        dismiss || listen && state.captionsVisible ->
            model.getApplication<android.app.Application>().uiCopy("listening.close", locale = state.uiLocale)
        listen -> model.getApplication<android.app.Application>().uiCopy("listening.start", locale = state.uiLocale)
        else -> registered.action.label
    }
    ElasticAction(registered.key, label, registered.action.icon.vector(registered.action.symbol),
        { when { listen -> model.toggleCaptionPanel(service); dismiss -> model.dismissCaptionPanel(); else -> model.invoke(registered) } },
        iconOnly = registered.action.buttonStyle == UiButtonStyle.ICON, busy = registered.action.busy,
        onLongClick = listenSettings.takeIf { listen })
}
