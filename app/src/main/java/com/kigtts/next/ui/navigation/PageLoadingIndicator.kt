package com.kigtts.next.ui.navigation

import com.kigtts.next.i18n.uiText
import com.kigtts.next.theme.ThemeText

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun PageLoadingIndicator(loading: Boolean, modifier: Modifier = Modifier) {
    val description = uiText("accessibility.loading")
    AnimatedVisibility(loading, modifier, enter = fadeIn(tween(140)) + expandHorizontally(tween(180), expandFrom = Alignment.Start),
        exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180), shrinkTowards = Alignment.Start)) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp).testTag("page-loading")
                .semantics { contentDescription = description }, strokeWidth = 2.dp)
        }
    }
}

@Composable
internal fun PageBarTitle(title: String, compact: Boolean, loading: Boolean) {
    Row(Modifier.cameraPullEntryRegion(), verticalAlignment = Alignment.CenterVertically) {
        ThemeText(title, Modifier.weight(1f, fill = false).testTag("page-title"),
            maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        PageLoadingIndicator(compact && loading, Modifier.padding(start = 4.dp))
    }
}
