package com.kigtts.next.state

import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager

/** Generic service/navigation outcomes stay outside home state and contain no engine implementation. */
internal suspend fun handleServiceOutcome(outcome: PluginOutcome, caller: KigPlugin, manager: PluginManager, context: PluginContext,
    stop: suspend () -> Unit, refresh: suspend () -> Unit, select: (String) -> Unit, navigate: (Destination) -> Unit): Boolean {
    when (outcome) {
        is PluginOutcome.SelectRecognitionService -> select(outcome.pluginId)
        PluginOutcome.RecognitionRoutingChanged -> { stop(); refresh() }
        is PluginOutcome.SetPluginEnabled -> {
            require(caller.asr?.info?.role == AsrRole.SERVICE) { "只有识别服务可以管理识别引擎启停" }
            val target = manager.packages.installed().first { it.manifest.id == outcome.pluginId }
            require("asr-service" !in target.manifest.capabilities && target.manifest.capabilities.any { it in setOf("asr-batch", "asr-segmented", "asr-incremental") })
            stop(); manager.enable(outcome.pluginId, outcome.enabled); refresh()
        }
        is PluginOutcome.OpenPluginPage -> {
            val target = manager.registry.plugin(outcome.pluginId)
            check(target != null && target.page(outcome.pageId, context) != null) { "引擎页面不可用，请先启用对应插件" }
            navigate(Destination.PluginPage(outcome.pluginId, outcome.pageId))
        }
        else -> return false
    }
    return true
}
