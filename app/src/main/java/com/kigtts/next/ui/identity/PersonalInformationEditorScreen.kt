package com.kigtts.next.ui.identity

import androidx.compose.runtime.Composable
import com.kigtts.next.state.AppViewModel

/** Search links can open the same profile directly in edit mode. */
@Composable internal fun PersonalInformationEditorScreen(model: AppViewModel) {
    PersonalCardsScreen(model, initiallyEditing = true)
}