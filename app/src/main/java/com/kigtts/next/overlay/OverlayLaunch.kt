package com.kigtts.next.overlay

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import com.kigtts.next.MainActivity
import com.kigtts.next.manager.HostPluginRuntime
import com.kigtts.next.state.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID

/** Exported MainActivity accepts only one-use, host-issued opaque routes, not arbitrary plugin actions. */
internal object OverlayLaunch {
    private const val EXTRA = "com.kigtts.next.OVERLAY_ROUTE"
    private val routes = linkedMapOf<String, Pair<Long, OverlayShortcut>>()
    @Synchronized fun intent(context: Context, item: OverlayShortcut): Intent {
        routes.entries.removeAll { SystemClock.elapsedRealtime() - it.value.first > 120_000 }
        while (routes.size >= 24) routes.remove(routes.keys.first())
        val key = UUID.randomUUID().toString(); routes[key] = SystemClock.elapsedRealtime() to item
        return Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA, key)
    }
    @Synchronized fun consume(intent: Intent?): OverlayShortcut? {
        val key = intent?.getStringExtra(EXTRA) ?: return null
        intent.removeExtra(EXTRA)
        return routes.remove(key)?.takeIf { SystemClock.elapsedRealtime() - it.first <= 120_000 }?.second
    }
    fun open(context: Context, item: OverlayShortcut) {
        try {
            require(item.enabled) { "这个入口暂时不可用" }
            val intent = if (item.component == null) intent(context, item) else {
                val component = requireNotNull(ComponentName.unflattenFromString(item.component))
                val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val info = context.packageManager.resolveActivity(launch, 0)?.activityInfo
                require(info != null && info.exported && info.enabled) { "应用不可用，请重新选择入口" }; launch
            }
            val internalPage = intent.component?.packageName == context.packageName
            val requested = overlayRequestsSmallWindow(OverlayStore.get(context).settings.value.preferSmallWindows,
                internalPage, OverlayHostWindow.fullscreenInForeground)
            val options = if (requested) OverlaySmallWindow.options(context, intent.component?.packageName ?: context.packageName) else null
            try {
                context.startActivity(intent, options)
            } catch (failure: RuntimeException) {
                if (!requested) throw failure
                context.startActivity(intent)
                Toast.makeText(context, "小窗未能打开，已正常打开", Toast.LENGTH_SHORT).show()
            }
        } catch (failure: Exception) {
            Toast.makeText(context, failure.message?.take(80) ?: "暂时无法打开入口", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun overlayRequestsSmallWindow(prefer: Boolean, internalPage: Boolean, fullscreenHost: Boolean) =
    prefer && !(internalPage && fullscreenHost)

internal fun AppViewModel.overlayLink(intent: Intent?) {
    val item = OverlayLaunch.consume(intent) ?: return
    viewModelScope.launch {
        if (item.owner != null) {
            if (registry.plugin(item.owner) == null) refreshPlugins()
            val installed = pluginManager.status.singleOrNull { it.installed.manifest.id == item.owner }?.installed
            val plugin = registry.plugin(item.owner)
            if (installed?.enabled != true || installed.publisherSha256 != item.publisher || plugin == null || !registry.available(plugin)) {
                message("这个插件入口暂时不可用"); return@launch
            }
            if (item.target.startsWith("page:")) {
                val page = item.target.removePrefix("page:")
                if (plugin.page(page, context()) != null) navigate(Destination.PluginPage(item.owner, page)) else message("这个页面已不存在")
            } else item.action?.let { invoke(item.owner, it) }
        } else when (item.target) {
            "home" -> { closeSidePage(); navigate(Destination.Home) }
            "camera" -> { openSidePage(SidePage.CARDS); cardCapture.open() }
            "cards" -> openSidePage(SidePage.CARDS)
            "sketch" -> openSidePage(SidePage.SKETCH)
            "quick-text" -> { navigate(Destination.Home); openPanel(HomePanel.QUICK_TEXT) }
            "listen" -> toggleListening()
            "speak" -> speak()
            "settings" -> navigate(Destination.Settings)
            "overlay-settings" -> navigate(Destination.SettingsDetail(SettingsCategory.OVERLAY))
        }
    }
}
