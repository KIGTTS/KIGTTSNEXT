package com.kigtts.next.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.kigtts.next.R
import com.kigtts.next.theme.ThemeIcon

/** Both vector layers use the same tight artboard and retain the gap inside the original wordmark. */
@Composable
internal fun OnboardingWordmark(modifier: Modifier = Modifier) {
    val letters = painterResource(R.drawable.logo_welcome_letters)
    val symbol = painterResource(R.drawable.logo_welcome_symbol)
    Box(modifier.aspectRatio(letters.intrinsicSize.width / letters.intrinsicSize.height)) {
        ThemeIcon(letters, "KIGTTS", Modifier.matchParentSize(), tint = MaterialTheme.colorScheme.onBackground)
        ThemeIcon(symbol, null, Modifier.matchParentSize(), tint = MaterialTheme.colorScheme.primary)
    }
}
