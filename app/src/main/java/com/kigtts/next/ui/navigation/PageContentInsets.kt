package com.kigtts.next.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Scroll viewports extend behind the gesture area; only their last item needs clearance. */
internal val LocalPageBottomInset = compositionLocalOf { 0.dp }

@Composable internal fun pageContentPadding(space: Dp = 16.dp) = PaddingValues(
    start = space, top = space, end = space, bottom = space + LocalPageBottomInset.current)
