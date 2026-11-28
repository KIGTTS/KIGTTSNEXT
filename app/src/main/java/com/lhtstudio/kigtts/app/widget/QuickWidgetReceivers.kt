package com.lhtstudio.kigtts.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.kigtts.next.desktop.ProfileCardWidget
import com.kigtts.next.desktop.QuickEntryWidget

// Keep the installed components so an upgrade can update widgets already on the launcher.
class QuickCardWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProfileCardWidget
}

class QuickEntryWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickEntryWidget
}
