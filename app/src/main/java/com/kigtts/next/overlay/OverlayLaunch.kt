package com.kigtts.next.overlay

import android.app.ActivityOptions
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
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
            val intent = if (item.component == null) intent(context, item) else {
                val component = requireNotNull(ComponentName.unflattenFromString(item.component))
                val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val info = context.packageManager.resolveActivity(launch, 0)?.activityInfo
                require(info != null && info.exported && info.enabled) { "应用不可用，请重新选择入口" }; launch
            }
            val freeform = item.smallWindow && context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
            val options = if (freeform) ActivityOptions.makeBasic().apply {
                val metrics = context.resources.displayMetrics
                setLaunchBounds(Rect((metrics.widthPixels * .1f).toInt(), (metrics.heightPixels * .12f).toInt(),
                    (metrics.widthPixels * .9f).toInt(), (metrics.heightPixels * .88f).toInt()))
            }.toBundle() else null
            context.startActivity(intent, options)
            if (item.smallWindow && !freeform) Toast.makeText(context, "系统未开放小窗，已正常打开", Toast.LENGTH_SHORT).show()
        } catch (failure: Exception) {
            Toast.makeText(context, failure.message?.take(80) ?: "暂时无法打开入口", Toast.LENGTH_SHORT).show()
        }
    }
}

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
            "home" -> navigate(Destination.Home)
            "camera" -> { openSidePage(SidePage.CARDS); cardCapture.open() }
            "cards" -> openSidePage(SidePage.CARDS)
            "sketch" -> openSidePage(SidePage.SKETCH)
            "quick-text" -> { navigate(Destination.Home); openPanel(HomePanel.QUICK_TEXT) }
            "overlay-settings" -> navigate(Destination.SettingsDetail(SettingsCategory.OVERLAY))
        }
    }
}
