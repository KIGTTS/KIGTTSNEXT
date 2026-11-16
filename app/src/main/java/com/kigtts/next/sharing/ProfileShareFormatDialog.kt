package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.feedbackChange
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.next.ui.settings.groupedSettingShape

@Composable internal fun ProfileShareFormatDialog(sharing: SharingCoordinator) {
    val includeSource by sharing.embedLongImage.collectAsStateWithLifecycle()
    MotionAlertDialog(onDismissRequest = sharing::dismissProfileFormat,
        title = { Text("分享个人主页") },
        text = { ProfileShareFormatTiles(includeSource, sharing::setEmbedLongImage, sharing::shareProfile) },
        confirmButton = { TextButton(dialogFeedbackClick(sharing::dismissProfileFormat)) { Text("取消") } })
}

@Composable
internal fun ProfileShareFormatTiles(includeSource: Boolean, setIncludeSource: (Boolean) -> Unit,
    share: (ProfileShareFormat) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val outer = MaterialTheme.shapes.medium
    val inner = CornerSize(4.dp)
    val bottomStart = RoundedCornerShape(inner, inner, inner, outer.bottomStart)
    val bottomEnd = RoundedCornerShape(inner, inner, outer.bottomEnd, inner)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(Modifier.fillMaxWidth().testTag("profile-format-long"), shape = groupedSettingShape(SettingPosition.FIRST, outer),
            color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
            Column {
                Surface(onClick = dialogFeedbackClick { share(ProfileShareFormat.LONG_IMAGE) },
                    modifier = Modifier.fillMaxWidth().testTag("profile-share-long"), color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Image, contentDescription = null, Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text("分享长图", style = MaterialTheme.typography.titleMedium)
                            Text("链接二维码附在图底", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(20.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.onPrimaryContainer.copy(alpha = .14f))
                Row(Modifier.fillMaxWidth().testTag("profile-embed-source")
                    .toggleable(includeSource, role = Role.Switch, onValueChange = feedbackChange(setIncludeSource))
                    .semantics { contentDescription = "嵌入源数据" }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("嵌入源数据", style = MaterialTheme.typography.labelLarge)
                        Text("用于还原图片磁贴；按图片发送时，压缩可能丢失源数据。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(includeSource, onCheckedChange = null)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ShareFormatTile("PDF", "适合保存和打印", Icons.Filled.PictureAsPdf, bottomStart,
                Modifier.weight(1f).fillMaxHeight().testTag("profile-share-pdf")) { share(ProfileShareFormat.PDF) }
            ShareFormatTile("离线网页", "可点开图片和联系方式", Icons.Filled.Language, bottomEnd,
                Modifier.weight(1f).fillMaxHeight().testTag("profile-share-mhtml")) { share(ProfileShareFormat.MHTML) }
        }
    }
}

@Composable
private fun ShareFormatTile(label: String, hint: String, icon: ImageVector, shape: RoundedCornerShape,
    modifier: Modifier, onShare: () -> Unit) {
    Surface(onClick = dialogFeedbackClick(onShare), modifier = modifier, shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
