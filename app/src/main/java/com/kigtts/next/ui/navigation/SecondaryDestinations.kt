package com.kigtts.next.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.kigtts.next.state.*
import com.kigtts.next.ui.plugins.*
import com.kigtts.next.ui.settings.*
import com.kigtts.next.ui.media.*

/** Page content is independent of its lifetime and transition in the navigation host. */
@Composable
fun SecondaryDestination(destination: Destination, state: AppState, model: AppViewModel) {
    val title = when (destination) {
            Destination.Home -> "KIGTTS"
            Destination.Settings -> "设置"
            is Destination.SettingsDetail -> destination.category.title
            Destination.Plugins -> "插件管理"
            is Destination.PluginPage -> model.registry.plugin(destination.pluginId)?.page(destination.pageId, model.context())?.title ?: "插件页面"
            is Destination.MediaLibrary -> "媒体库"
            is Destination.ContentEditor -> "内容编辑器"
        }
    PageScaffold(title,model::navigateBack) {
            when (destination) {
                Destination.Home -> Unit
                Destination.Settings -> SettingsScreen(model::navigate)
                is Destination.SettingsDetail -> when (destination.category) {
                    SettingsCategory.APPEARANCE -> AppearanceSettingsScreen(state.theme, model::theme)
                    SettingsCategory.FEEDBACK -> FeedbackSettingsScreen(state.hapticsEnabled, model::haptics)
                    SettingsCategory.EXPERIMENTAL -> ExperimentalSettingsScreen(state.appleLivePhotos, model::appleLivePhotos)
                }
                Destination.Plugins -> PluginsScreen(model)
                is Destination.MediaLibrary -> MediaLibraryScreen(model, destination.target)
                is Destination.ContentEditor -> ContentEditorScreen(model, destination.token)
                is Destination.PluginPage -> PluginPageScreen(model.registry.plugin(destination.pluginId)?.page(destination.pageId, model.context()), destination.pluginId in state.busyPlugins,
                    resolve = { resource -> model.pluginManager.paths.existingResource(resource.pluginId ?: destination.pluginId, resource.path) }) { action, inputs ->
                    model.invoke(destination.pluginId, action, inputs)
                }
            }
    }
}
