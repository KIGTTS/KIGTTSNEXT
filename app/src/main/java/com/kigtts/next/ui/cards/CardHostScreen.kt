package com.kigtts.next.ui.cards
import androidx.compose.runtime.Composable
import com.kigtts.next.state.AppViewModel
@Composable fun CardHostScreen(model: AppViewModel) {
    com.kigtts.next.ui.album.AlbumHost(model, model::navigateBack)
}
