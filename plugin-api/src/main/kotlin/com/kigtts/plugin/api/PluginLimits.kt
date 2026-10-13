package com.kigtts.plugin.api

/** Broken plugins must not make an unbounded UI tree or collide in the host's action namespace. */
internal fun validateSnapshot(snapshot: PluginSnapshot) {
    val contributions = snapshot.contributions
    require(contributions.actions.size <= 500 && contributions.drawerEntries.size <= 64 && contributions.gestures.size <= 32)
    require(contributions.automationActions.size <= 64 && contributions.automationActions.distinct().size == contributions.automationActions.size)
    contributions.automationActions.forEach { require(it.length in 1..128) }
    require(contributions.settingsEntries.size <= 64 && contributions.settingsEntries.map { it.id }.distinct().size == contributions.settingsEntries.size)
    contributions.settingsEntries.forEach { require(it.id.length in 1..128 && it.pageId.length in 1..128 && it.label.length in 1..160 && it.summary.length <= 1000 && it.cardTitle.length <= 160 && it.cardId.matches(Regex("[a-z][a-z0-9.-]{0,63}"))) }
    require(contributions.managedPackages.size <= 256 && contributions.managedPackages.map { it.id }.distinct().size == contributions.managedPackages.size)
    contributions.managedPackages.forEach { require(it.id.matches(Regex("[a-z][a-z0-9.-]{1,127}")) && it.version.length in 1..64 && it.kind.length in 1..32) }
    contributions.sourceAction?.let { require(it in contributions.automationActions) }
    require((contributions.actions.map { it.id } + contributions.gestures.map { it.id }).let { it.distinct().size == it.size })
    contributions.actions.forEach {
        require(it.id.length in 1..128 && it.label.length in 1..160)
        require((it.slot == UiSlot.EDITOR_MENU) == (it.presentation == UiPresentation.MENU_ITEM))
    }
    require(snapshot.pages.size <= 64 && snapshot.pages.map { it.id }.distinct().size == snapshot.pages.size)
    var count = 0
    fun blocks(items: List<PageBlock>, depth: Int) {
        require(depth <= 8)
        for (block in items) {
            require(++count <= 1000)
            when (block) {
                is PageBlock.Text -> require(block.text.length <= 16_000)
                is PageBlock.Notice -> require(block.text.length <= 16_000)
                is PageBlock.SourceEditor -> require(block.id.length in 1..128 && block.text.length <= 64_000 && block.saveAction.length in 1..128 && block.language.length <= 32)
                is PageBlock.Files -> {
                    require(block.items.size <= 1000 && block.items.map { it.path }.distinct().size == block.items.size)
                    require(block.title.length <= 160 && block.directory.length <= 240)
                    block.items.forEach { require(it.path.length in 1..240 && it.name.length in 1..240 && it.bytes >= 0) }
                    listOfNotNull(block.openAction, block.operationAction, block.importAction, block.createAction, block.parentAction).forEach { require(it.length in 1..128) }
                }
                is PageBlock.Toggle -> require(block.title.length <= 160 && block.summary.length <= 1000 && block.actionId.length in 1..128)
                is PageBlock.Input -> require(block.title.length <= 160 && block.value.length <= 64_000 && block.actionId.length in 1..128)
                is PageBlock.Choice -> {
                    require(block.title.length <= 160 && block.options.size in 1..12)
                    require(block.options.map { it.id }.distinct().size == block.options.size)
                    block.options.forEach { require(it.id.length in 1..128 && it.label.length in 1..160) }
                }
                is PageBlock.Chat -> {
                    require(block.messages.size <= 100 && block.messages.map { it.id }.distinct().size == block.messages.size)
                    require(block.sendAction.length in 1..128 && block.stopAction.length in 1..128)
                    block.messages.forEach { require(it.id.length in 1..128 && it.text.length <= 64_000 && it.reasoning.length <= 64_000) }
                }
                is PageBlock.Group -> { require(block.title.length <= 160); blocks(block.children, depth + 1) }
                is PageBlock.Actions -> {
                    require(block.actions.size <= 100)
                    block.actions.forEach { require(it.id.length in 1..128 && it.label.length in 1..160) }
                }
            }
        }
    }
    snapshot.pages.forEach { require(it.id.length in 1..128 && it.title.length in 1..160); blocks(it.blocks, 0) }
}
