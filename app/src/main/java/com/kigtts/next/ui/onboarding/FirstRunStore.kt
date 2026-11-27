package com.kigtts.next.ui.onboarding

import android.content.Context
import com.kigtts.next.state.QuickPhraseStore
import com.kigtts.next.state.defaultQuickTextPresetIds

internal enum class FirstRunStep(val page: String) {
    WELCOME("welcome"),
    APPEARANCE("appearance"),
    FEATURES("features"),
    PRESETS("presets"),
    PERMISSIONS("permissions"),
    COMPLETE("complete"),
}

internal enum class HomeTip { PANEL, CURSOR, QUICK_EDIT, SUBTITLE_MENU }

/** The next step and the tips already shown survive process death independently. */
internal class FirstRunStore(context: Context, existingInstall: Boolean = false, upgradingOriginal: Boolean = false) {
    private val preferences = context.getSharedPreferences("first-run", Context.MODE_PRIVATE)
    init {
        val saved = preferences.getString("step", null)
        if (saved == null && upgradingOriginal) {
            preferences.edit().putBoolean("original-upgrade", true).putString("step", FirstRunStep.WELCOME.name).apply()
        } else if (saved == "AGREEMENT" || saved == null) {
            val initial = when {
                saved == "AGREEMENT" -> FirstRunStep.APPEARANCE
                existingInstall -> FirstRunStep.COMPLETE
                else -> FirstRunStep.WELCOME
            }
            preferences.edit().putString("step", initial.name).apply()
        }
        if (step() != FirstRunStep.COMPLETE) QuickPhraseStore.prepareForOnboarding(context)
    }

    val upgrade: Boolean get() = preferences.getBoolean("original-upgrade", false)
    val pages: List<FirstRunStep> get() = if (upgrade) upgradePages else newInstallPages

    fun step(): FirstRunStep = FirstRunStep.entries.firstOrNull { it.name == preferences.getString("step", null) }
        ?: FirstRunStep.WELCOME

    fun agreementAccepted(): Boolean = preferences.getBoolean("agreement_accepted", false)

    fun presetGroups(): Set<String> = preferences.getStringSet("preset_groups", null)?.toSet() ?: defaultQuickTextPresetIds

    fun savePresetGroups(groups: Set<String>) {
        preferences.edit().putStringSet("preset_groups", groups).apply()
    }

    fun visit(page: FirstRunStep) {
        if (step() == FirstRunStep.COMPLETE || page !in pages) return
        if (step() != page) preferences.edit().putString("step", page.name).apply()
    }

    fun finish(agreementConfirmed: Boolean): Boolean {
        if (step() != pages.last() || !upgrade && !agreementConfirmed) return false
        preferences.edit()
            .putBoolean("agreement_accepted", agreementConfirmed || agreementAccepted())
            .putString("step", FirstRunStep.COMPLETE.name)
            .apply()
        return true
    }

    fun seen(tip: HomeTip): Boolean = preferences.getBoolean("tip_${tip.name}", false)
    fun markSeen(tip: HomeTip) { preferences.edit().putBoolean("tip_${tip.name}", true).apply() }

    private companion object {
        val newInstallPages = FirstRunStep.entries.filter { it != FirstRunStep.COMPLETE }
        val upgradePages = listOf(FirstRunStep.WELCOME, FirstRunStep.APPEARANCE, FirstRunStep.FEATURES)
    }
}
