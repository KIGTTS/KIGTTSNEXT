package com.kigtts.next.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

/** Task feedback belongs beside Back and never changes the page content's measured position. */
@Composable
internal fun PageBackNavigation(back: () -> Unit, label: String, tag: String, loading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(feedbackClick(back), Modifier.testTag(tag)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, label) }
        AnimatedVisibility(loading, enter = fadeIn(tween(140)) + expandHorizontally(tween(180),expandFrom=Alignment.Start),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180),shrinkTowards=Alignment.Start)) {
            Box(Modifier.padding(end = 8.dp).size(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp).testTag("page-loading")
                    .semantics { contentDescription = "正在加载" }, strokeWidth = 2.dp)
            }
        }
    }
}
