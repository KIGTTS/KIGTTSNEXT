@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.kigtts.next.ui.navigation.*

/** Window chrome floats above the photographs without reserving an app-bar-height strip. */
@Composable
internal fun CardLandscapeHeader(title: String, back: () -> Unit, backLabel: String,
    actions: List<ToolbarAction>, content: @Composable () -> Unit) {
    val host = remember { PageToolbarActions() }
    val guardedBack = { if (host.backGuard?.value?.invoke() != true) back() }
    CompositionLocalProvider(LocalPageToolbarActions provides host, LocalPageBackAction provides back) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("immersive-page")) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) { content() }
            TopAppBar(title = {
                Column {
                    PageBarTitle(title, compact = true, loading = host.loading?.value == true)
                    Text("下滑进行拍照", Modifier.testTag("page-subtitle").cameraPullEntryRegion(),
                        style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, modifier = Modifier.testTag("small-page-bar"),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                navigationIcon = { PageBackNavigation(guardedBack, backLabel, "page-back", false) },
                actions = { PageActionButtons(actions + (host.source?.value ?: emptyList())) })
        }
    }
}
