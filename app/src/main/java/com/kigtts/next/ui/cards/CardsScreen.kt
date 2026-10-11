package com.kigtts.next.ui.cards

import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.next.ui.components.MotionTextButton

import com.kigtts.next.ui.components.feedbackClick

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction

@Composable
fun CardsScreen() {
    val context = LocalContext.current
    val store = remember { CardStore(context) }
    var cards by remember { mutableStateOf(store.load()) }
    var adding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        imageUri = uri?.toString()
        if (uri != null) runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    RegisterPageActions(listOf(ToolbarAction("add-card","添加名片",Icons.Default.Add) { adding=true }))
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            cards.forEachIndexed { index, card ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(Modifier.fillMaxWidth().height(280.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                            val image by produceState<android.graphics.Bitmap?>(null, card.imageUri) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching {
                                        card.imageUri?.let { uri -> context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                                            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                                        } }
                                    }.getOrNull()
                                }
                            }
                            if (image != null) Image(image!!.asImageBitmap(), card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(card.title, style = MaterialTheme.typography.titleMedium); Text(card.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            MotionIconButton(onClick = feedbackClick {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${card.title}\n${card.description}") }, "分享名片"))
                            }) { Icon(Icons.Default.Share, "分享 ${card.title}") }
                            MotionIconButton(onClick = feedbackClick { cards = cards.filterIndexed { i, _ -> i != index }; store.save(cards) }) { Icon(Icons.Default.DeleteOutline, "删除 ${card.title}") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(70.dp))
        }
    }
    if (adding) MotionAlertDialog(onDismissRequest = { adding = false }, title = { Text("新名片") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("名称") })
            OutlinedTextField(description, { description = it }, label = { Text("说明") })
            FilledTonalButton(onClick = feedbackClick { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (imageUri == null) "选择图片" else "已选择图片 · 更换") }
        }
    }, confirmButton = { MotionTextButton(enabled = title.isNotBlank(), onClick = dialogFeedbackClick { cards = cards + ContactCard(title.trim(), description.trim(), imageUri); store.save(cards); adding = false; title = ""; description = ""; imageUri = null }) { Text("添加") } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick { adding = false }) { Text("取消") } })
}
