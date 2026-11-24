package com.kigtts.next.ui.onboarding

import android.content.Context

internal enum class FirstRunStep(val page: String) {
    WELCOME("welcome"),
    AGREEMENT("agreement"),
    APPEARANCE("appearance"),
    FEATURES("features"),
    PERMISSIONS("permissions"),
    COMPLETE("complete"),
}

internal enum class HomeTip(val anchor: String) {
    PANEL("dock-handle"), CURSOR("cursor-track"), QUICK_EDIT("dock-start"),
}

/** The next step and the tips already shown survive process death independently. */
internal class FirstRunStore(context: Context, existingInstall: Boolean = false) {
    private val preferences = context.getSharedPreferences("first-run", Context.MODE_PRIVATE)
    init {
        if (!preferences.contains("agreement_accepted")) {
            val previous = preferences.getString("step", null)
            val initial = when {
                previous == FirstRunStep.PERMISSIONS.name -> FirstRunStep.APPEARANCE
                previous != null -> step()
                existingInstall -> FirstRunStep.COMPLETE
                else -> FirstRunStep.WELCOME
            }
            preferences.edit().putString("step", initial.name)
                .putBoolean("agreement_accepted", initial.ordinal > FirstRunStep.AGREEMENT.ordinal)
                .apply()
        }
    }

    fun step(): FirstRunStep = FirstRunStep.entries.firstOrNull { it.name == preferences.getString("step", null) }
        ?: FirstRunStep.WELCOME

    fun agreementAccepted(): Boolean = preferences.getBoolean("agreement_accepted", false)

    fun acceptAgreement() {
        preferences.edit().putBoolean("agreement_accepted", true).apply()
    }

    fun visit(page: FirstRunStep) {
        if (step() == FirstRunStep.COMPLETE || page == FirstRunStep.COMPLETE) return
        if (!agreementAccepted() && page.ordinal > FirstRunStep.AGREEMENT.ordinal) return
        preferences.edit().putString("step", page.name).apply()
    }

    fun finish(): Boolean {
        if (step() != FirstRunStep.PERMISSIONS || !agreementAccepted()) return false
        preferences.edit().putString("step", FirstRunStep.COMPLETE.name).apply()
        return true
    }

    fun seen(tip: HomeTip): Boolean = preferences.getBoolean("tip_${tip.name}", false)
    fun markSeen(tip: HomeTip) { preferences.edit().putBoolean("tip_${tip.name}", true).apply() }
}
