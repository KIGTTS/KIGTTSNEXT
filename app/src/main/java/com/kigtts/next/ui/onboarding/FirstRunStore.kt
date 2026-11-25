package com.kigtts.next.ui.onboarding

import android.content.Context

internal enum class FirstRunStep(val page: String) {
    WELCOME("welcome"),
    APPEARANCE("appearance"),
    FEATURES("features"),
    PERMISSIONS("permissions"),
    COMPLETE("complete"),
}

internal enum class HomeTip { PANEL, CURSOR, QUICK_EDIT, SUBTITLE_MENU }

/** The next step and the tips already shown survive process death independently. */
internal class FirstRunStore(context: Context, existingInstall: Boolean = false) {
    private val preferences = context.getSharedPreferences("first-run", Context.MODE_PRIVATE)
    init {
        val saved = preferences.getString("step", null)
        if (saved == "AGREEMENT" || saved == null) {
            val initial = when {
                saved == "AGREEMENT" -> FirstRunStep.APPEARANCE
                existingInstall -> FirstRunStep.COMPLETE
                else -> FirstRunStep.WELCOME
            }
            preferences.edit().putString("step", initial.name).apply()
        }
    }

    fun step(): FirstRunStep = FirstRunStep.entries.firstOrNull { it.name == preferences.getString("step", null) }
        ?: FirstRunStep.WELCOME

    fun agreementAccepted(): Boolean = preferences.getBoolean("agreement_accepted", false)

    fun visit(page: FirstRunStep) {
        if (step() == FirstRunStep.COMPLETE || page == FirstRunStep.COMPLETE) return
        if (step() != page) preferences.edit().putString("step", page.name).apply()
    }

    fun finish(agreementConfirmed: Boolean): Boolean {
        if (step() != FirstRunStep.PERMISSIONS || !agreementConfirmed) return false
        preferences.edit()
            .putBoolean("agreement_accepted", true)
            .putString("step", FirstRunStep.COMPLETE.name)
            .apply()
        return true
    }

    fun seen(tip: HomeTip): Boolean = preferences.getBoolean("tip_${tip.name}", false)
    fun markSeen(tip: HomeTip) { preferences.edit().putBoolean("tip_${tip.name}", true).apply() }
}
