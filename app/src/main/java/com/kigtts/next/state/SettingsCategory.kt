package com.kigtts.next.state

enum class SettingsCategory(val title: String, val summary: String) {
    APPEARANCE("外观", "主题、颜色、对比度和圆角"),
    FEEDBACK("触感反馈", "按钮与长按的震动反馈"),
    EXPERIMENTAL("实验性功能", "尝试可选的新功能"),
}
