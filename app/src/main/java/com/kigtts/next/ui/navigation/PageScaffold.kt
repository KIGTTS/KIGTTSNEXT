@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.theme.*
import androidx.compose.ui.graphics.Color
import com.kigtts.next.i18n.uiText

/** The page background covers the full window; only its controls consume safe drawing insets. */
@Composable
fun PageScaffold(title:String,back:()->Unit,modifier:Modifier=Modifier,backLabel:String=uiText("nav.back"),largeBar:Boolean=true,
    actions:List<ToolbarAction> = emptyList(),backTag:String="page-back",subtitle:String?=null,extendToBottom:Boolean=false,
    backgroundSettings:com.kigtts.next.theme.ThemeSettings?=null,contentScrollsHeader:Boolean=true,contentHandlesIme:Boolean=false,
    backgroundDecoration:(@Composable BoxScope.()->Unit)?=null,content:@Composable ()->Unit) {
    val host=remember { PageToolbarActions() }
    val active=rememberUpdatedState(LocalPageActive.current)
    val barState=rememberTopAppBarState()
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val multiWindow = com.kigtts.next.ui.components.rememberMultiWindowMode()
    val drawing = if (contentHandlesIme) WindowInsets.systemBars.union(WindowInsets.displayCutout) else WindowInsets.safeDrawing
    val safe = if (multiWindow) drawing.union(WindowInsets.systemGestures) else drawing
    val bottomClearance = if (extendToBottom) with(density) { safe.getBottom(density).toDp() } else androidx.compose.ui.unit.Dp(0f)
    val keyboard by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val input = rememberUpdatedState(largeBar && (host.input.focused || keyboard))
    val snap = remember { tween<Float>(220) }
    val canScroll = remember(active, input) { { active.value && !input.value } }
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState, snapAnimationSpec = snap, canScroll = canScroll)
    // Match Material's switch between the expanded and single-line title semantics.
    // Do not recompose the entire editor for each pixel of the app bar or IME animation.
    val compact by remember(largeBar, barState) { derivedStateOf { !largeBar || barState.collapsedFraction >= .5f } }
    LaunchedEffect(input.value,barState.heightOffsetLimit) {
        if(largeBar&&input.value&&barState.heightOffsetLimit>-Float.MAX_VALUE) {
            Animatable(barState.heightOffset).animateTo(barState.heightOffsetLimit,tween(180)) { barState.heightOffset=value }
        }
    }
    val guardedBack = { if (host.backGuard?.value?.invoke() != true) back() }
    val settings = backgroundSettings ?: LocalAppAppearanceSettings.current
    CompositionLocalProvider(LocalPageToolbarActions provides host, LocalPageBackAction provides back,
        LocalPageBottomInset provides if (extendToBottom) bottomClearance else androidx.compose.ui.unit.Dp(0f)) {
        SecondaryPageSurface(settings, Modifier.fillMaxSize()) {
        val background = LocalSecondaryBackground.current
        backgroundDecoration?.invoke(this)
        // Sheets own their vertical scroll chain; the bar still supports direct dragging.
        Scaffold(modifier.fillMaxSize().then(if(largeBar&&contentScrollsHeader)Modifier.nestedScroll(behavior.nestedScrollConnection) else Modifier).testTag("immersive-page"),
            containerColor=Color.Transparent,
            contentColor=if (background == null && backgroundDecoration == null) contentColorFor(MaterialTheme.colorScheme.background) else LocalContentColor.current,
            contentWindowInsets=safe.only(if(extendToBottom)WindowInsetsSides.Horizontal else WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom),
            topBar={ if(largeBar)LargeFlexibleTopAppBar(title={ PageBarTitle(title,compact,host.loading) },Modifier.testTag("large-page-bar"),
                colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent,scrolledContainerColor=Color.Transparent),
                subtitle=subtitle?.let { text -> { ThemeText(text, Modifier.testTag("page-subtitle").cameraPullEntryRegion(), maxLines=1, overflow=TextOverflow.Ellipsis) } },
                windowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal),
                navigationIcon={ PageBackNavigation(guardedBack,backLabel,backTag,host.loading&&!compact) },
                actions={ PageActionButtons(actions+(host.source?.value ?: emptyList())) },scrollBehavior=behavior)
            else TopAppBar(title={ Column { PageBarTitle(title,true,host.loading)
                subtitle?.let { ThemeText(it, Modifier.testTag("page-subtitle").cameraPullEntryRegion(), style=MaterialTheme.typography.labelMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1, overflow=TextOverflow.Ellipsis) }
            } },modifier=Modifier.testTag("small-page-bar"),
                colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent,scrolledContainerColor=Color.Transparent),
                windowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal),
                navigationIcon={ PageBackNavigation(guardedBack,backLabel,backTag,false) },
                actions={ PageActionButtons(actions+(host.source?.value ?: emptyList())) }) }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) { content() }
        }
        }
    }
}
