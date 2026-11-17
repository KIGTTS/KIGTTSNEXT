package com.kigtts.next.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal val subtitleControlsInset = 8.dp

@Composable
internal fun subtitleCardColor(hasBackdrop: Boolean) =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (hasBackdrop) .88f else 1f)
