package com.kigtts.next.ui.media

import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.media.MediaRepository

/** Recheck referenced file stamps off-thread when returning from another app or plugin update. */
@Composable
fun ObserveMediaPreviews(repository: MediaRepository) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(repository, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) repository.thumbnails.refresh() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
}
