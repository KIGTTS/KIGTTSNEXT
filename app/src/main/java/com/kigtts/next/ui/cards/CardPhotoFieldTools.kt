package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.*
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

@Composable
internal fun CardPhotoFieldTools(cards: CardRepository, card: PolaroidCard, enabled: Boolean,
    put: (CardLayer) -> Unit, remove: (String) -> Unit, message: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(card.id) { mutableStateOf(false) }
    fun toggle(field: PhotoTextField, selected: Boolean) {
        if (busy || !enabled) return
        if (selected) { cards.designs.fieldLayers(card, field).forEach { remove(it.id) }; return }
        busy = true
        scope.launch {
            try {
                val existing = withContext(Dispatchers.IO) {
                    if (!cards.designs.hasFacts(card.id)) cards.designs.freeze(card.id,
                        PolaroidPhotoFacts.read(cards.stickers.imageFile(card.originalImageId ?: card.imageId.orEmpty()), false))
                    cards.designs.facts(card.id)
                }
                val facts = PolaroidPhotoFacts.enrich(context, existing, setOf(field))
                withContext(Dispatchers.IO) { cards.designs.freeze(card.id, facts) }
                cards.designs.fieldLayers(card, field).forEach { if (it.id != field.layerId()) remove(it.id) }
                put(photoInformationLayer(field, facts.text(field)))
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "未能取得拍摄信息") }
            finally { busy = false }
        }
    }
    CardEditorSection("拍摄信息", Icons.Default.Event, "card-capture-fields") {
        PhotoTextField.entries.forEach { field ->
            val selected = cards.designs.fieldLayers(card, field).isNotEmpty()
            val click = feedbackClick { toggle(field, selected) }
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("photo-field/" + field.name)
                .toggleable(selected, enabled = enabled && !busy, role = Role.Switch,
                    onValueChange = { click() }),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(MaterialSymbols.vector(field.symbol) ?: Icons.Default.Event, null, Modifier.size(22.dp))
                Text(field.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(selected, null, enabled = enabled && !busy)
            }
        }
    }
}
