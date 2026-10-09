package com.kigtts.next.state

enum class SettingsCategory(val title: String, val summary: String) {
    COLORS("主题与颜色", "主题模式、壁纸取色和主题色"),
    DISPLAY("显示与形状", "对比度、更暗的深色模式和圆角"),
    FEEDBACK("触感反馈", "按钮与长按的震动反馈"),
    EXPERIMENTAL("实验性功能", "苹果实况照片等可选功能"),
}
