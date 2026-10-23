package com.kigtts.next.ui.plugins

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.PluginSearch
import kotlinx.coroutines.delay

/** Search owns its input and Back action; typing never opens an input dialog. */
@Composable
internal fun PluginSearchScreen(search: PluginSearch, back: () -> Unit,
    invoke: (String, Map<String, String>) -> Unit, content: @Composable () -> Unit) {
    var query by remember(search.actionId) { mutableStateOf(search.query) }
    var submitted by remember(search.actionId) { mutableStateOf(search.query) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val currentInvoke by rememberUpdatedState(invoke)
    val exit = { keyboard?.hide(); back() }
    BackHandler(onBack = exit)
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    LaunchedEffect(query) {
        if (query != submitted) {
            delay(220)
            submitted = query
            currentInvoke(search.actionId, mapOf("value" to query))
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag("plugin-search-page")) {
            TextField(query, { if (it.length <= 100) query = it },
                Modifier.fillMaxWidth().focusRequester(focus).testTag("plugin-search-input"), singleLine = true,
                placeholder = { Text(search.placeholder) },
                leadingIcon = { IconButton(feedbackClick(exit)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出搜索") } },
                trailingIcon = { if (query.isNotEmpty()) IconButton(feedbackClick { query = "" }) { Icon(Icons.Default.Close, "清除搜索") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    submitted = query; currentInvoke(search.actionId, mapOf("value" to query)); keyboard?.hide()
                }),
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            HorizontalDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}
