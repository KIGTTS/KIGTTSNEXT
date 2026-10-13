package com.kigtts.next.ui.cards

/** An accepted close belongs to the window, not to an animation coroutine's dispatcher. */
internal class CardPreviewExit {
    private var pending: (() -> Unit)? = null
    fun accept(action: () -> Unit) { check(pending == null); pending = action }
    fun finish() { val action = pending ?: return; pending = null; action() }
}
