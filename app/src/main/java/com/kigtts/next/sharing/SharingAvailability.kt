package com.kigtts.next.sharing

internal const val CLOUD_SHARING_ENABLED = false
internal val pausedSharingPlugins = if (CLOUD_SHARING_ENABLED) emptySet() else setOf(CLOUD_SHARING_ID)
