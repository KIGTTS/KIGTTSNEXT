package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.cards.generators.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

@Composable internal fun GeneratedStickerDialog(layer: CardLayer, source: GeneratedSticker, stickers: StickerRepository,
    close: () -> Unit, save: (CardLayer) -> Unit) {
    var values by remember(layer.id) { mutableStateOf(source.values) }
    var color by remember { mutableStateOf<StickerParameter?>(null) }
    var working by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun update(id: String, value: String) { values = values + (id to value) }
    MotionAlertDialog({ if (!working) close() }, title = { Text(source.spec.name) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            source.spec.parameters.forEach { p -> val value = values[p.id] ?: p.default
                when (p.kind) {
                    StickerParameterKind.TEXT -> OutlinedTextField(value, { update(p.id, it.take(p.maxLength)) },
                        Modifier.fillMaxWidth(), label = { Text(p.label) }, enabled = !working)
                    StickerParameterKind.COLOR -> TextButton(feedbackClick { color = p }, enabled = !working) { Text(p.label); Spacer(Modifier.width(8.dp)); Text(value) }
                    StickerParameterKind.NUMBER -> Column {
                        Text(p.label + " · " + value)
                        Slider(value.toFloatOrNull() ?: p.default.toFloat(), { update(p.id, it.toString()) },
                            valueRange = p.minimum.toFloat()..p.maximum.toFloat(), enabled = !working)
                    }
                    StickerParameterKind.CHOICE -> Column {
                        Text(p.label); p.choices.forEach { option ->
                            Row { RadioButton(value == option, feedbackClick { update(p.id, option) }, enabled = !working); Text(option) }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(feedbackClick {
        working = true; scope.launch {
            try {
                val id = withContext(Dispatchers.IO) { GeneratedStickers(stickers).generate(source, values) }
                save(layer.copy(imageId = id)); close()
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error = failure.message ?: "贴纸未能生成，请重试" }
            finally { working = false }
        }
    }, enabled = !working) { Text(uiText("action.done")) } }, dismissButton = { TextButton(feedbackClick(close), enabled = !working) { Text(uiText("action.cancel")) } })
    color?.let { p -> ColorPickerDialog(android.graphics.Color.parseColor(values[p.id] ?: p.default).toLong() and 0xFFFFFFFFL,
        { selected -> update(p.id, "#%06X".format(selected and 0xFFFFFF)); color = null }, { color = null }, title = p.label) }
}
