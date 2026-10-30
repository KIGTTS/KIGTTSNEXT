package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kigtts.next.R

@Composable internal fun AboutBrand(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.logo_horizontal), "KIGTTS 横版字标",
            Modifier.width(224.dp).height(56.dp).testTag("about-logo"), tint = MaterialTheme.colorScheme.onSurface)
    }
}