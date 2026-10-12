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

/** The page background covers the full window; only its controls consume safe drawing insets. */
@Composable
fun PageScaffold(title:String,back:()->Unit,modifier:Modifier=Modifier,backLabel:String="返回上一页",largeBar:Boolean=true,
    actions:List<ToolbarAction> = emptyList(),backTag:String="page-back",content:@Composable ()->Unit) {
    val host=remember { PageToolbarActions() }
    val active=rememberUpdatedState(LocalPageActive.current)
    val barState=rememberTopAppBarState()
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val keyboard by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val input = rememberUpdatedState(host.input.focused || keyboard)
    val behavior=TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState,snapAnimationSpec=tween(220),
        canScroll={ active.value && !input.value })
    // Match Material's switch between the expanded and single-line title semantics.
    // Do not recompose the entire editor for each pixel of the app bar or IME animation.
    val compact by remember(largeBar, barState) { derivedStateOf { !largeBar || barState.collapsedFraction >= .5f } }
    LaunchedEffect(input.value,barState.heightOffsetLimit) {
        if(largeBar&&input.value&&barState.heightOffsetLimit>-Float.MAX_VALUE) {
            Animatable(barState.heightOffset).animateTo(barState.heightOffsetLimit,tween(180)) { barState.heightOffset=value }
        }
    }
    CompositionLocalProvider(LocalPageToolbarActions provides host) {
        Scaffold(modifier.fillMaxSize().then(if(largeBar)Modifier.nestedScroll(behavior.nestedScrollConnection) else Modifier).testTag("immersive-page"),
            containerColor=MaterialTheme.colorScheme.background,
            contentWindowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom),
            topBar={ if(largeBar)LargeFlexibleTopAppBar(title={ PageBarTitle(title,compact,host.loading?.value==true) },Modifier.testTag("large-page-bar"),
                windowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal),
                navigationIcon={ PageBackNavigation(back,backLabel,backTag,host.loading?.value==true&&!compact) },
                actions={ PageActionButtons(actions+(host.source?.value ?: emptyList())) },scrollBehavior=behavior)
            else TopAppBar(title={ PageBarTitle(title,true,host.loading?.value==true) },modifier=Modifier.testTag("small-page-bar"),
                windowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal),
                navigationIcon={ PageBackNavigation(back,backLabel,backTag,false) },
                actions={ PageActionButtons(actions+(host.source?.value ?: emptyList())) }) }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) { content() }
        }
    }
}
