package com.kigtts.next.ui.navigation

import com.kigtts.next.theme.ThemeIcon

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
import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.next.ui.components.compactPlainIconColors

/** Expanded bars show feedback beside Back; compact bars own it in their title. */
@Composable
internal fun PageBackNavigation(back: () -> Unit, label: String, tag: String, loading: Boolean) {
    Row(Modifier.excludeCameraPull(), verticalAlignment = Alignment.CenterVertically) {
        MotionIconButton(feedbackClick(back), Modifier.testTag(tag), colors = compactPlainIconColors()) {
            ThemeIcon(Icons.AutoMirrored.Filled.ArrowBack, label)
        }
        PageLoadingIndicator(loading, Modifier.padding(end = 8.dp))
    }
}
