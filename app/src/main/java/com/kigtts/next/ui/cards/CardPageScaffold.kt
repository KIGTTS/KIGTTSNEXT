@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.navigation.*
import com.kigtts.next.theme.LocalUseCompactControlPalette

/** Window geometry, rather than device orientation, decides how much space the title may use. */
@Composable
fun CardPageScaffold(title: String, back: () -> Unit, modifier: Modifier = Modifier,
    backLabel: String = "返回上一页", actions: List<ToolbarAction> = emptyList(),
    subtitle: String? = "下滑进行拍照", content: @Composable () -> Unit) {
    val host = remember { PageToolbarActions() }
    val chrome = remember { CardPageChromeState() }
    val barState = rememberTopAppBarState()
    val active = rememberUpdatedState(LocalPageActive.current)
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val keyboard by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val forceCompact = chrome.albums?.value == true || host.input.focused || keyboard
    val forced = rememberUpdatedState(forceCompact)
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState, snapAnimationSpec = tween(220),
        canScroll = { active.value && !forced.value })
    val compact by remember(barState) { derivedStateOf { barState.collapsedFraction >= .5f } }
    val guardedBack = { if (host.backGuard?.value?.invoke() != true) back() }
    CompositionLocalProvider(LocalUseCompactControlPalette provides true) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        LaunchedEffect(forceCompact, landscape, barState.heightOffsetLimit) {
            if (!landscape && barState.heightOffsetLimit > -Float.MAX_VALUE) {
                Animatable(barState.heightOffset).animateTo(if (forceCompact) barState.heightOffsetLimit else 0f, tween(220)) {
                    barState.heightOffset = value
                }
            }
        }
        CompositionLocalProvider(LocalPageToolbarActions provides host, LocalPageBackAction provides back,
            LocalCardPageChrome provides chrome, LocalCardWindowLandscape provides landscape) {
            Scaffold(Modifier.fillMaxSize().then(if (!landscape) Modifier.nestedScroll(behavior.nestedScrollConnection) else Modifier)
                .testTag("immersive-page"), containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                topBar = {
                    if (landscape) TopAppBar(title = { Column {
                        PageBarTitle(title, true, host.loading?.value == true)
                        subtitle?.let { Text(it, Modifier.testTag("page-subtitle").cameraPullEntryRegion(),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } }, modifier = Modifier.cardToolbarSpace(chrome).testTag("small-page-bar"),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        navigationIcon = { PageBackNavigation(guardedBack, backLabel, "page-back", false) },
                        actions = { PageActionButtons(actions + (host.source?.value ?: emptyList())) })
                    else LargeFlexibleTopAppBar(title = { PageBarTitle(title, compact, host.loading?.value == true) },
                        modifier = Modifier.testTag("large-page-bar"), subtitle = subtitle?.let { text -> {
                            Text(text, Modifier.testTag("page-subtitle").cameraPullEntryRegion(), maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        } }, windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        navigationIcon = { PageBackNavigation(guardedBack, backLabel, "page-back", host.loading?.value == true && !compact) },
                        actions = { PageActionButtons(actions + (host.source?.value ?: emptyList())) }, scrollBehavior = behavior)
                }) { insets ->
                // One content invocation keeps the browser/pager alive when only the chrome rotates.
                val padding = if (landscape) PaddingValues(start = insets.calculateLeftPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                    end = insets.calculateRightPadding(androidx.compose.ui.platform.LocalLayoutDirection.current), bottom = insets.calculateBottomPadding()) else insets
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else Modifier)) {
                    val headerClearance = if (landscape) insets.calculateTopPadding() - with(density) { WindowInsets.safeDrawing.getTop(density).toDp() }
                        else 0.dp
                    CompositionLocalProvider(LocalCardHeaderClearance provides headerClearance) { content() }
                }
            }
        }
    }
    }
}
