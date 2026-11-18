@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

@Composable
internal fun CardDesignTools(cards: CardRepository, draft: PolaroidCard, enabled: Boolean,
    change: (PolaroidCard) -> Unit, message: (String) -> Unit) {
    val state by cards.designs.state.collectAsState()
    val scope = rememberCoroutineScope()
    val saveFailed = uiText("photo.save_preset_failed")
    val applyFailed = uiText("photo.apply_preset_failed")
    var naming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun work(action: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(saveFailed) }
            finally { busy = false }
        }
    }
    CardEditorSection(uiText("photo.frame_presets"), Icons.Default.Bookmarks, "card-frame-presets") {
        PillButton(uiText("photo.save_preset"), Icons.Default.BookmarkAdd, { naming = true }, enabled = enabled && !busy)
        Text(uiText("photo.preset_hint"), style = MaterialTheme.typography.bodySmall)
        state.presets.forEach { preset ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.defaultId == preset.id, { work { cards.designs.defaultPreset(preset.id) } }, enabled = enabled && !busy)
                Text(preset.name, Modifier.weight(1f))
                ToolButton(uiText("photo.apply_preset"), Icons.Default.Check, {
                    scope.launch {
                        try { val card = withContext(Dispatchers.IO) { cards.designs.apply(draft, preset.id) }; change(card) }
                        catch (error: Exception) { message(applyFailed) }
                    }
                }, enabled = enabled && !busy)
                ToolButton(uiText("photo.remove_preset"), Icons.Default.DeleteOutline, { work { cards.designs.removePreset(preset.id) } }, enabled = enabled && !busy)
            }
        }
        if (state.defaultId != null) TextButton({ work { cards.designs.defaultPreset(null) } }, enabled = enabled && !busy) { Text(uiText("photo.preset_none")) }
    }
    if (naming) CardNameDialog(uiText("photo.save_frame_preset"), "", { naming = false }, { name ->
        naming = false; work { cards.designs.savePreset(name, draft, makeDefault = true) }
    }, maximum = 64, inputLabel = uiText("photo.preset_name"))
}
