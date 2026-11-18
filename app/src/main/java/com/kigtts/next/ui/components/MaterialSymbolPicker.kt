package com.kigtts.next.ui.components

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable internal fun MaterialSymbolPicker(select: (String) -> Unit, close: () -> Unit, selected: String? = null) {
    var query by remember { mutableStateOf("") }; var offset by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf(emptyList<Pair<String, ImageVector?>>()) }; var more by remember { mutableStateOf(false) }
    LaunchedEffect(query, offset) {
        delay(120)
        val found = withContext(Dispatchers.Default) { MaterialSymbols.search(query, offset, 81).map { it to MaterialSymbols.vector(it) } }
        more = found.size > 80; items = found.take(80)
    }
    MotionAlertDialog(onDismissRequest = close, title = { Text("Material Symbols") }, text = {
        Column {
            OutlinedTextField(query, { query = it.take(80); offset = 0 }, singleLine = true, label = { Text(uiText("symbol.search_english")) }, modifier = Modifier.testTag("symbol-search"))
            LazyVerticalGrid(GridCells.Adaptive(72.dp), Modifier.height(320.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(items, key = { it.first }) { (name, vector) ->
                    val leave = rememberDialogClose()
                    TextButton(onClick = feedbackClick { leave { select(name) } }, modifier = Modifier.testTag("symbol/" + name).semantics { this.selected = selected == name }) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { vector?.let { Icon(it, null) }; Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2) }
                } }
            }
            Row { if (offset > 0) MotionTextButton(onClick = feedbackClick { offset = (offset - 80).coerceAtLeast(0) }) { Text(uiText("pagination.previous")) }
                if (more) MotionTextButton(onClick = feedbackClick { offset += 80 }) { Text(uiText("pagination.next")) } }
        }
    }, confirmButton = { MotionTextButton(onClick = dialogFeedbackClick(close)) { Text(uiText("action.cancel")) } })
}
