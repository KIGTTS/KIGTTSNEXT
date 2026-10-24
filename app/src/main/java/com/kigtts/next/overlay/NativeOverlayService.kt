package com.kigtts.next.overlay

import android.app.*
import android.content.Context
import android.content.ComponentCallbacks
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import androidx.compose.runtime.*
import com.kigtts.next.R
import com.kigtts.next.state.FeedbackSettingsStore
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.ProvideButtonFeedback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow

/** Native application feature. Its tiny collapsed window never captures the rest of the display. */
class NativeOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: OverlayStore
    private lateinit var hub: OverlayHub
    private lateinit var wm: WindowManager
    private lateinit var windowContext: Context
    private val windowConfiguration = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) { dragX = 0f; dragY = 0f; place() }
        override fun onLowMemory() = Unit
    }
    private var dial: OverlayWindow? = null
    private var bubble: OverlayWindow? = null
    private var expanded by mutableStateOf(false)
    private var hidden by mutableStateOf(false)
    private var layout by mutableStateOf(OverlayPlacement(0, 0, 80, 80, 40f, 40f))
    private var config by mutableStateOf(OverlaySettings())
    private var theme by mutableStateOf(ThemeSettings())
    private var haptics by mutableStateOf(true)
    private var windowExpanded = false
    private var retraction = 0f
    private var dragX = 0f; private var dragY = 0f
    private var idle: Job? = null
    private var collapseReady = true
    private lateinit var appearance: SharedPreferences
    private lateinit var feedback: SharedPreferences
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        theme = ThemeSettingsStore(this).load(); haptics = FeedbackSettingsStore(this).load()
        com.kigtts.next.ui.components.MaterialSymbols.initialize(this)
    }
    override fun onCreate() {
        super.onCreate()
        store = OverlayStore.get(this); hub = OverlayHub.get(this)
        config = store.settings.value
        if (!config.enabled || !Settings.canDrawOverlays(this)) { stopSelf(); return }
        try {
            windowContext = if (Build.VERSION.SDK_INT >= 30) {
                val display = requireNotNull(getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
                createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } else this
            wm = windowContext.getSystemService(WindowManager::class.java)
            foreground()
        } catch (_: Exception) { store.update { it.copy(enabled = false) }; stopSelf(); return }
        theme = ThemeSettingsStore(this).load(); haptics = FeedbackSettingsStore(this).load()
        com.kigtts.next.ui.components.MaterialSymbols.initialize(this)
        appearance = getSharedPreferences("appearance", MODE_PRIVATE); feedback = getSharedPreferences("interaction", MODE_PRIVATE)
        appearance.registerOnSharedPreferenceChangeListener(preferenceListener); feedback.registerOnSharedPreferenceChangeListener(preferenceListener)
        dial = OverlayWindow(windowContext, wm) { collapse() }.also { host -> host.content {
            KigttsTheme(theme) { ProvideButtonFeedback(haptics) {
                OverlayDial(config, layout, expanded, hidden, ::toggle, ::drag, ::drop, ::interact, ::open,
                    { open(OverlayShortcut("overlay-settings", "悬浮窗设置", "tune", "overlay-settings")) }, ::finishCollapse, { id, direction ->
                        val before = store.settings.value.shortcuts.indexOfFirst { it.id == id }
                        store.move(id, direction)
                        before != store.settings.value.shortcuts.indexOfFirst { it.id == id }
                    }, retracted = { value ->
                        if (retraction != value) { retraction = value; place() }
                    })
            } }
        } }
        place(); interact()
        if (Build.VERSION.SDK_INT >= 31) windowContext.registerComponentCallbacks(windowConfiguration)
        val apps = MutableStateFlow(config.shortcuts.filter { it.component != null }.map { it.id }.toSet())
        scope.launch { combine(store.settings, hub.candidates, apps) { saved, candidates, installedApps ->
            saved.copy(shortcuts = resolveOverlayShortcuts(saved.shortcuts, candidates, installedApps))
        }.collectLatest { next ->
            val geometryChanged = config.rightEdge != next.rightEdge || config.position != next.position
            val behaviorChanged = config.copy(shortcuts = emptyList()) != next.copy(shortcuts = emptyList()) ||
                config.shortcuts.map { it.id } != next.shortcuts.map { it.id }
            config = next
            if (!next.enabled || !Settings.canDrawOverlays(this@NativeOverlayService)) { stopSelf(); return@collectLatest }
            if (geometryChanged) place(animate = true)
            if (behaviorChanged) interact()
        } }
        scope.launch { hub.bubble.collectLatest { value ->
            bubble?.close(); bubble = null
            if (value != null && validNotice(value)) {
                hidden = false; showBubble(value); interact()
            }
        } }
        scope.launch { while (isActive) {
            val runtime = com.kigtts.next.manager.HostPluginRuntime.get(application)
            refreshOverlayPlugins(this@NativeOverlayService, runtime.manager)
            apps.value = availableOverlayApps(this@NativeOverlayService, store.settings.value.shortcuts)
            delay(2000)
            if (!Settings.canDrawOverlays(this@NativeOverlayService)) { store.update { it.copy(enabled = false) }; stopSelf(); break }
            hub.bubble.value?.takeUnless(::validNotice)?.let { hub.clear(it.owner) }
        } }
    }
    private fun validNotice(notice: OverlayNotice): Boolean {
        val runtime = com.kigtts.next.manager.HostPluginRuntime.get(application)
        return !runtime.manager.safeMode() && runtime.manager.permissions.access("${notice.owner}:${notice.publisher}", "overlay") != com.kigtts.plugin.runtime.PermissionAccess.DENY && runtime.manager.status.any {
            it.installed.manifest.id == notice.owner && it.installed.enabled && it.installed.publisherSha256 == notice.publisher
        }
    }
    private fun safeBounds(): OverlayBounds {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val b = metrics.bounds
            return OverlayBounds(b.left + insets.left, b.top + insets.top,
                (b.width() - insets.left - insets.right).coerceAtLeast(1), (b.height() - insets.top - insets.bottom).coerceAtLeast(1))
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        fun dimension(name: String): Int { val id = windowContext.resources.getIdentifier(name, "dimen", "android"); return if (id == 0) 0 else windowContext.resources.getDimensionPixelSize(id) }
        val top = dimension("status_bar_height"); val bottom = dimension("navigation_bar_height")
        return OverlayBounds(0, top, metrics.widthPixels, (metrics.heightPixels - top - bottom).coerceAtLeast(1))
    }
    private fun place(animate: Boolean = false) {
        val safe = safeBounds()
        layout = overlayPlacement(safe, windowContext.resources.displayMetrics.density, config.rightEdge, config.position, windowExpanded, retraction)
        if (dragX != 0f || dragY != 0f) layout = layout.copy(x = layout.x + dragX.toInt(), y = (layout.y + dragY.toInt()).coerceIn(safe.top, safe.top + (safe.height - layout.height).coerceAtLeast(0)))
        try { dial?.place(layout.x, layout.y, layout.width, layout.height, animate) }
        catch (_: RuntimeException) { store.update { it.copy(enabled = false) }; stopSelf() }
        hub.bubble.value?.let { if (bubble != null) placeBubble() }
    }
    private fun interact() {
        idle?.cancel(); hidden = false
        if (!expanded && config.autoHide && hub.bubble.value == null) idle = scope.launch { delay(3500); hidden = true }
    }
    private fun toggle() { if (expanded) collapse() else {
        interact(); collapseReady = false; windowExpanded = true; place(); expanded = true
    } }
    private fun collapse() {
        if (!expanded) return
        expanded = false; collapseReady = false
    }
    private fun finishCollapse() {
        if (expanded) return
        collapseReady = true
        if (dragX != 0f || dragY != 0f || !windowExpanded) return
        windowExpanded = false; place(); interact()
    }
    private fun drag(x: Float, y: Float) {
        if (expanded) collapse()
        interact(); dragX += x; dragY += y; place()
    }
    private fun drop() {
        if (dragX == 0f && dragY == 0f) {
            if (collapseReady) finishCollapse()
            return
        }
        val safe = safeBounds(); val centerX = layout.x + layout.fabX; val centerY = layout.y + layout.fabY
        dragX = 0f; dragY = 0f
        // Collapse around the current absolute center before crossing edges. Otherwise the
        // local anchor flips from the right to the left of the large host in one frame.
        if (windowExpanded) {
            windowExpanded = false
            val compact = overlayPlacement(safe, windowContext.resources.displayMetrics.density, config.rightEdge, config.position, false)
            layout = compact.copy(x = (centerX - compact.fabX).toInt(), y = (centerY - compact.fabY).toInt())
            dial?.place(layout.x, layout.y, layout.width, layout.height)
        }
        val padding = 40 * windowContext.resources.displayMetrics.density
        val position = ((centerY - safe.top - padding) / (safe.height - 2 * padding).coerceAtLeast(1f)).coerceIn(0f, 1f)
        store.update { it.copy(rightEdge = centerX > safe.left + safe.width / 2f, position = position) }
        config = store.settings.value; place(animate = true); interact()
    }
    private fun open(item: OverlayShortcut) { collapse(); OverlayLaunch.open(this, item) }
    private fun showBubble(value: OverlayNotice) {
        bubble = OverlayWindow(windowContext, wm) {}.also { host -> host.content {
            KigttsTheme(theme) { ProvideButtonFeedback(haptics) { OverlayBubbleContent(value, { hub.clear(value.owner) }, { hub.clear(value.owner); open(it) }) } }
        } }
        placeBubble()
    }
    private fun placeBubble() {
        val safe = safeBounds(); val d = windowContext.resources.displayMetrics.density
        val width = minOf((244 * d).toInt(), safe.width - (72 * d).toInt()).coerceAtLeast(1)
        val height = minOf((184 * d).toInt(), safe.height)
        val x = if (config.rightEdge) safe.left + safe.width - (72 * d).toInt() - width else safe.left + (72 * d).toInt()
        val y = (layout.y + layout.fabY - height / 2).toInt().coerceIn(safe.top, safe.top + safe.height - height)
        runCatching { bubble?.place(x, y, width, height) }.onFailure { bubble?.close(); bubble = null }
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); dragX = 0f; dragY = 0f; place() }
    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "悬浮快捷入口", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 180, Intent(this, com.kigtts.next.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 181, Intent(this, NativeOverlayService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_listening)
            .setContentTitle("悬浮快捷入口已开启").setContentText("长按悬浮按钮可调整入口").setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "关闭悬浮窗", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(180, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(180, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { store.update { it.copy(enabled = false) }; stopSelf() }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel(); dial?.close(); bubble?.close()
        if (Build.VERSION.SDK_INT >= 31 && ::windowContext.isInitialized) windowContext.unregisterComponentCallbacks(windowConfiguration)
        if (::appearance.isInitialized) appearance.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::feedback.isInitialized) feedback.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "native-overlay"; private const val STOP = "close-native-overlay"
        fun sync(context: Context): Boolean {
            val store = OverlayStore.get(context)
            if (!store.settings.value.enabled) { context.stopService(Intent(context, NativeOverlayService::class.java)); return true }
            if (!Settings.canDrawOverlays(context)) return false
            return runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, NativeOverlayService::class.java)); true }.getOrElse {
                store.update { it.copy(enabled = false) }; false
            }
        }
    }
}
