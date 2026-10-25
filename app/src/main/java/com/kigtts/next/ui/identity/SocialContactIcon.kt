package com.kigtts.next.ui.identity

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.kigtts.next.identity.ContactLink
import com.kigtts.next.identity.socialPlatform

@Composable
internal fun SocialContactIcon(link: ContactLink, modifier: Modifier = Modifier) {
    Icon(painterResource(socialPlatform(link).icon), contentDescription = null, modifier = modifier)
}
