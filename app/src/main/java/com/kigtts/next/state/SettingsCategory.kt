package com.kigtts.next.state

enum class SettingsCategory(val title: String, val summary: String) {
    APPEARANCE("外观", "主题、颜色与圆角"),
    FEEDBACK("操作反馈", "震动与长按快速排序"),
    EXPERIMENTAL("实验性功能", "体验新功能"),
    WEB("网页", "管理网页登录信息"),
    BACKUP_RESTORE("备份与还原", "导入、导出快捷文本及其它兼容格式"),
    CAMERA("拍照", "拍立得相机与照片导入"),
}
