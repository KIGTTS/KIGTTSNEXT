package com.kigtts.next.links

import com.kigtts.next.i18n.uiText

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

@Composable fun ExternalLinkHost(model: AppViewModel) {
    val pending by model.externalLinks.pending.collectAsState()
    val link = pending ?: return
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.surface.toArgb()
    fun open() {
        try {
            model.externalLinks.perform(link)
            if (link.mode == WebOpenMode.CUSTOM_TAB) {
                // Pin a browser package so an app-link handler cannot loop back into this Activity.
                val packageName = androidx.browser.customtabs.CustomTabsClient.getPackageName(context, null)
                val tab = CustomTabsIntent.Builder().setShowTitle(true)
                    .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()).build()
                if (packageName != null) tab.intent.setPackage(packageName)
                else tab.intent.setPackage(context.packageManager.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://example.org")), 0)
                    .firstOrNull { it.activityInfo.packageName != context.packageName }?.activityInfo?.packageName
                    ?: throw ActivityNotFoundException())
                tab.launchUrl(context, Uri.parse(requireNotNull(link.url)))
            }
        } catch (_: ActivityNotFoundException) { model.message(model.hostCopy("link.browser_required")) }
        catch (e: Exception) { model.message(e.message ?: "无法打开这个链接") }
        finally { model.externalLinks.dismiss() }
    }
    if (link.trusted && !link.add) { LaunchedEffect(link) { open() }; return }
    MotionAlertDialog(onDismissRequest = model.externalLinks::dismiss,
        title = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (link.add) "添加到快捷文本？" else if (link.url != null) "即将打开第三方网站" else "打开快捷文本？", Modifier.weight(1f))
            Icon(Icons.Default.OpenInNew, null, Modifier.size(24.dp))
        } }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (link.title.isNotBlank()) Text(link.title, style = MaterialTheme.typography.titleMedium)
            Text(link.url ?: link.text.take(500))
            if (link.url != null && !link.trusted) Text(uiText("link.third_party"), style = MaterialTheme.typography.bodyMedium)
        } }, confirmButton = { Button(dialogFeedbackClick(::open)) { Text(if (link.add) "添加并打开" else "继续打开") } },
        dismissButton = { TextButton(dialogFeedbackClick(model.externalLinks::dismiss)) { Text(uiText("action.cancel")) } })
}
