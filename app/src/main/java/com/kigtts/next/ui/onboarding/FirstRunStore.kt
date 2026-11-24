package com.kigtts.next.ui.onboarding

import android.content.Context

internal enum class FirstRunStep { AGREEMENT, PERMISSIONS, APPEARANCE, FEATURES, COMPLETE }

internal enum class HomeTip(val anchor: String) {
    PANEL("dock-handle"), CURSOR("cursor-track"), QUICK_EDIT("dock-start"),
}

/** The next step and the tips already shown survive process death independently. */
internal class FirstRunStore(context: Context, existingInstall: Boolean = false) {
    private val preferences = context.getSharedPreferences("first-run", Context.MODE_PRIVATE)
    private val initial = if (existingInstall) FirstRunStep.COMPLETE else FirstRunStep.AGREEMENT

    init {
        // Record the initial decision on first launch so a later APK update cannot skip an unfinished guide.
        if (!preferences.contains("step")) preferences.edit().putString("step", initial.name).apply()
    }

    fun step(): FirstRunStep = FirstRunStep.entries.firstOrNull { it.name == preferences.getString("step", null) }
        ?: FirstRunStep.AGREEMENT

    fun advance(from: FirstRunStep): FirstRunStep {
        val current = step()
        if (current != from || current == FirstRunStep.COMPLETE) return current
        return save(FirstRunStep.entries[current.ordinal + 1])
    }

    fun back(from: FirstRunStep): FirstRunStep {
        if (step() != from || from !in setOf(FirstRunStep.APPEARANCE, FirstRunStep.FEATURES)) return step()
        return save(FirstRunStep.entries[from.ordinal - 1])
    }

    private fun save(next: FirstRunStep): FirstRunStep {
        preferences.edit().putString("step", next.name).apply()
        return next
    }

    fun seen(tip: HomeTip): Boolean = preferences.getBoolean("tip_${tip.name}", false)
    fun markSeen(tip: HomeTip) { preferences.edit().putBoolean("tip_${tip.name}", true).apply() }
}
