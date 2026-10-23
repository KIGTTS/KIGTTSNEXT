package com.kigtts.next.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Center scrollable content without stretching rows across a tablet or desktop window. */
@Composable
internal fun ReadablePage(maximumWidth: Dp = 1120.dp, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = maximumWidth).fillMaxSize(), content = content)
    }
}
