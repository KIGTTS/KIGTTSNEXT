package com.kigtts.next.state

enum class SettingsCategory(val title: String, val summary: String) {
    APPEARANCE("外观", "主题、颜色与圆角"),
    FEEDBACK("触感反馈", "点击与长按震动"),
    EXPERIMENTAL("实验性功能", "体验新功能"),
    WEB("网页", "管理网页登录信息"),
}
