package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.SettingsCategory
import com.kigtts.next.theme.AppearanceCardSurface
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.plugins.ChatRichText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AboutDocument(val title: String, val asset: String, val markdown: Boolean = false)

internal val aboutLegalDocuments = mapOf(
    SettingsCategory.USER_AGREEMENT to AboutDocument("用户协议", "legal/user_agreement.md", true),
    SettingsCategory.PRIVACY_POLICY to AboutDocument("隐私政策", "legal/privacy_policy.md", true),
)

internal val aboutLicenses = listOf(
    AboutDocument("KIGTTS · MIT License", "legal/app-license.txt"),
    AboutDocument("界面与图标组件", "licenses/NOTICE-ui.txt"),
    AboutDocument("相机与二维码组件", "licenses/NOTICE-camera-qr.txt"),
    AboutDocument("地点数据", "licenses/NOTICE-photo-places.txt"),
    AboutDocument("Apache License 2.0", "licenses/Apache-2.0.txt"),
    AboutDocument("Remix Icon", "licenses/Remix-Icon-License.txt"),
    AboutDocument("CommonMark · BSD 2-Clause", "licenses/commonmark-BSD-2-Clause.txt"),
    AboutDocument("Autolink · MIT License", "licenses/autolink-MIT.txt"),
    AboutDocument("Smartcrop · MIT License", "licenses/smartcrop-MIT.txt"),
)

@Composable internal fun AboutDocumentScreen(category: SettingsCategory) {
    SettingsLayout {
        AppearanceCardSurface(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(24.dp)) { AboutDocumentText(aboutLegalDocuments.getValue(category)) }
        }
    }
}

@Composable internal fun AboutLicensesScreen() {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsLayout {
        SettingsGroup("开源许可证") {
            aboutLicenses.forEachIndexed { index, document ->
                SettingItem(document.title, "", Icons.Default.Description,
                    when (index) { 0 -> SettingPosition.FIRST; aboutLicenses.lastIndex -> SettingPosition.LAST; else -> SettingPosition.MIDDLE },
                    onClick = { selected = document.asset })
            }
        }
    }
    aboutLicenses.firstOrNull { it.asset == selected }?.let { document ->
        MotionAlertDialog({ selected = null }, title = { Text(document.title) }, text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                AboutDocumentText(document)
            }
        }, confirmButton = {
            MotionTextButton(dialogFeedbackClick { selected = null }) { Text("关闭") }
        })
    }
}

@Composable private fun AboutDocumentText(document: AboutDocument) {
    val context = LocalContext.current
    val content by produceState<Result<String>?>(null, document.asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(document.asset).bufferedReader(Charsets.UTF_8).use { it.readText() } }
        }
    }
    val text = content?.getOrNull()
    when {
        text != null -> SelectionContainer { if (document.markdown) ChatRichText(text) else Text(text, style = MaterialTheme.typography.bodyMedium) }
        content != null -> Text("暂时无法读取内容")
        else -> CircularProgressIndicator(Modifier.size(24.dp))
    }
}
