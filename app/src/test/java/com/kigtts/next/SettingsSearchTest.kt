package com.kigtts.next

import com.kigtts.next.ui.settings.*
import com.kigtts.next.state.*
import org.junit.Assert.*
import org.junit.Test

class SettingsSearchTest {
    @Test fun resultsIncludeNativeOptionPathsAndDoNotLosePluginEntrances() {
        val cards = settingsCards(null, com.kigtts.plugin.api.PluginContext("", ""))
        assertTrue(searchSettings("震动", cards).any { it.destination == Destination.SettingsDetail(SettingsCategory.FEEDBACK) && it.path.contains("设置 / 操作反馈") })
        assertTrue(searchSettings("背景", cards).any { it.title == "背景图片" })
        assertTrue(searchSettings("二次返回", cards).any { it.title == "二次返回退出" && it.path.contains("返回操作") })
        assertTrue(searchSettings("联系方式", cards).any { it.destination == Destination.PersonalInformationEditor })
        assertTrue(searchSettings("   ", cards).isEmpty())
    }
}
