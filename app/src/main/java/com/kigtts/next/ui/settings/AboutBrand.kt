package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.R
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun AboutBrand(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val copy = rememberUiCopy()
    Box(modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.logo_horizontal), copy("about.wordmark"),
            Modifier.width(224.dp).height(56.dp).testTag("about-logo")
                .clickable(role = Role.Button, onClickLabel = copy("about.website"), onClick = feedbackClick(onClick)),
            tint = MaterialTheme.colorScheme.onSurface)
    }
}
