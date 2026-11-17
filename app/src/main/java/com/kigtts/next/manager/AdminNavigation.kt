package com.kigtts.next.manager

import com.kigtts.next.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Optional bridge-1 diagnostics use real navigation, including the application's normal return. */
suspend fun adminNavigate(model:AppViewModel,args:JSONObject,back:Boolean):JSONObject=withContext(Dispatchers.Main) {
    if(back) {
        if(model.state.value.sidePage!=null)model.closeSidePage() else model.navigateBack()
    } else when(args.getString("page")) {
        "home" -> { model.closeSidePage();model.navigate(Destination.Home) }
        "settings" -> model.navigate(Destination.Settings)
        "appearance" -> model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE))
        "feedback" -> model.navigate(Destination.SettingsDetail(SettingsCategory.FEEDBACK))
        "experimental" -> model.navigate(Destination.SettingsDetail(SettingsCategory.EXPERIMENTAL))
        "plugins" -> model.navigate(Destination.Plugins)
        "media" -> model.content.picker()
        "html-preview" -> {
            val entry = requireNotNull(model.mediaRepository.get(args.getString("mediaId"))) { "网页不存在" }
            require(entry.kind in setOf(com.kigtts.next.media.MediaKind.HTML, com.kigtts.next.media.MediaKind.WEB))
            model.navigate(Destination.HtmlPreview(entry.id))
        }
        "sketch" -> model.openSidePage(SidePage.SKETCH)
        "cards" -> model.openSidePage(SidePage.CARDS)
        "plugin" -> { val id=args.getString("id");require(model.registry.plugin(id)!=null);model.navigate(Destination.PluginPage(id,args.getString("pageId"))) }
        else -> error("未知调试页面")
    }
    JSONObject().put("destination",model.state.value.destination.toString())
}
