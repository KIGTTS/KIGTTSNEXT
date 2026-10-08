package com.kigtts.plugin.api

data class RegisteredAction(val plugin: KigPlugin, val action: UiAction) {
    val key: String get() = "${plugin.descriptor.id}/${action.id}"
}
data class RegisteredDrawerEntry(val plugin: KigPlugin, val entry: DrawerEntry)
data class RegisteredGesture(val plugin: KigPlugin, val action: GestureAction)

/** Explicit registration for this prototype; not an arbitrary APK/dex loader. */
class PluginRegistry(plugins: List<KigPlugin>) {
    val plugins: List<KigPlugin> = plugins.toList()
    init {
        require(this.plugins.map { it.descriptor.id }.distinct().size == this.plugins.size) { "Duplicate plugin ID" }
        this.plugins.forEach { plugin ->
            require(plugin.descriptor.id.matches(Regex("[a-z][a-z0-9.-]+"))) { "Invalid plugin ID" }
            require(plugin.descriptor.apiVersion == PLUGIN_API_VERSION) { "Incompatible plugin API" }
            val actions = plugin.contributions.actions
            require(actions.map { it.id }.distinct().size == actions.size) { "Duplicate action ID" }
            require(actions.all { it.id.isNotBlank() && it.label.isNotBlank() })
            require(actions.all { (it.slot == UiSlot.EDITOR_MENU) == (it.presentation == UiPresentation.MENU_ITEM) }) {
                "Menu items must use EDITOR_MENU; other slots require buttons"
            }
            val entries = plugin.contributions.drawerEntries
            require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate drawer entry ID" }
            require(entries.all { it.id.isNotBlank() && it.pageId.isNotBlank() && it.label.isNotBlank() })
            val gestures = plugin.contributions.gestures
            require((actions.map { it.id } + gestures.map { it.id }).distinct().size == actions.size + gestures.size) { "Duplicate gesture/action ID" }
            require(gestures.all { it.id.isNotBlank() && it.label.isNotBlank() })
        }
    }

    fun actions(slot: UiSlot, context: PluginContext = PluginContext("", "")): List<RegisteredAction> = plugins.flatMap { plugin ->
        plugin.contributions(context).actions.filter { it.slot == slot }.map { RegisteredAction(plugin, it) }
    }.sortedWith(compareBy({ it.action.order }, { it.key }))

    fun drawerEntries(): List<RegisteredDrawerEntry> = plugins.flatMap { plugin ->
        plugin.contributions.drawerEntries.map { RegisteredDrawerEntry(plugin, it) }
    }
    fun plugin(id: String): KigPlugin? = plugins.firstOrNull { it.descriptor.id == id }
    fun gestureActions(gesture: PluginGesture, context: PluginContext): List<RegisteredGesture> = plugins.flatMap { plugin ->
        plugin.contributions(context).gestures.filter { it.gesture == gesture }.map { RegisteredGesture(plugin, it) }
    }
}
