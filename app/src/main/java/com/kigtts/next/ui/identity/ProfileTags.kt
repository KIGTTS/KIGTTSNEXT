@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

@Composable internal fun ProfileTags(tags: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag -> Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(tag, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
        } }
    }
}

@Composable internal fun ProfileTagsEditor(tags: List<String>, close: () -> Unit, save: (List<String>) -> Unit) {
    var draft by remember { mutableStateOf(tags) }
    var text by remember { mutableStateOf("") }
    MotionAlertDialog(close, title = { Text(uiText("profile.tags_own")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { draft.forEach { tag ->
                InputChip(true, feedbackClick { draft = draft - tag }, label = { Text(tag) }, trailingIcon = { Text("×") })
            } }
            OutlinedTextField(text, { text = it.take(20) }, Modifier.fillMaxWidth().testTag("profile-tag-input"), label = { Text(uiText("profile.tag_new")) }, singleLine = true)
            TextButton(feedbackClick { val tag = text.trim(); if (tag.isNotBlank() && tag !in draft && draft.size < 12) { draft += tag; text = "" } }) { Text(uiText("profile.tag_add")) }
        }
    }, confirmButton = { TextButton(feedbackClick { val tag = text.trim(); save(if (tag.isNotBlank() && tag !in draft && draft.size < 12) draft + tag else draft) }) { Text(uiText("profile.tags_save")) } },
        dismissButton = { TextButton(dialogFeedbackClick(close)) { Text(uiText("action.cancel")) } })
}
