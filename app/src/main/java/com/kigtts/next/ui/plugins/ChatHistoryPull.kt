@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun ChatEarlierButton(loading: Boolean, load: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (loading) ContainedLoadingIndicator(Modifier.size(48.dp).then(Modifier.testTag("chat-history-loading")))
        else TextButton(onClick = feedbackClick(load)) { Text(uiText("chat.earlier")) }
    }
}
