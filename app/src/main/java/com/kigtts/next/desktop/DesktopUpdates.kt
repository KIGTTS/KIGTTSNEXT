package com.kigtts.next.desktop

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import com.kigtts.next.i18n.uiCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal object DesktopUpdates {
    private val requests = Channel<Context>(Channel.CONFLATED)
    private val updates = MutableStateFlow(0)
    val revision = updates.asStateFlow()
    private var published: Pair<List<Pair<Int, Int>>, List<String>>? = null

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            for (initial in requests) {
                delay(160)
                val context = requests.tryReceive().getOrNull() ?: initial
                updates.update { it + 1 }
                runUpdate("shortcuts") { publishShortcuts(context) }
                runUpdate("quick entry") { QuickEntryWidget.updateAll(context) }
                runUpdate("profile") { ProfileCardWidget.updateAll(context) }
            }
        }
    }

    fun refresh(context: Context) {
        requests.trySend(context.applicationContext)
    }

    private suspend fun runUpdate(label: String, action: suspend () -> Unit) {
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Log.w("KigttsDesktop", "Could not refresh $label", failure) }
    }

    private fun publishShortcuts(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val colors = DesktopAppearance.load(context).colors
        val fillings = listOf(colors.primaryContainer to colors.onPrimaryContainer, colors.secondaryContainer to colors.onSecondaryContainer,
            colors.tertiaryContainer to colors.onTertiaryContainer).map { (fill, ink) -> fill.toArgb() to ink.toArgb() }
        val labels = DesktopEntry.quick.map { context.uiCopy(it.label) }
        val current = fillings to labels
        if (current == published && manager.dynamicShortcuts.map { it.id }.toSet() == DesktopEntry.quick.map { it.id }.toSet()) return
        val shortcuts = DesktopEntry.quick.mapIndexed { rank, entry ->
            val (fill, ink) = fillings[rank]
            ShortcutInfo.Builder(context, entry.id)
                .setRank(rank)
                .setShortLabel(labels[rank])
                .setIcon(Icon.createWithBitmap(flowerIcon(context, entry.icon, fill, ink)))
                .setIntent(entry.intent(context))
                .build()
        }
        if (manager.setDynamicShortcuts(shortcuts)) published = current
    }
}
