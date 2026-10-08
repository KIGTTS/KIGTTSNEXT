package com.kigtts.plugin.api

/** Broken plugins must not make an unbounded UI tree or collide in the host's action namespace. */
internal fun validateSnapshot(snapshot: PluginSnapshot) {
    val contributions = snapshot.contributions
    require(contributions.actions.size <= 500 && contributions.drawerEntries.size <= 64 && contributions.gestures.size <= 32)
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
