package com.kigtts.next.state

enum class SettingsCategory(val title: String, val summary: String) {
    LANGUAGE("界面语言", ""),
    APPEARANCE("外观", "主题、颜色与圆角"),
    FEEDBACK("操作反馈", "震动与长按快速排序"),
    OVERLAY("悬浮窗", "悬浮快捷入口与气泡"),
    EXPERIMENTAL("实验性功能", "体验新功能"),
    WEB("网页", "管理网页登录信息"),
    BACKUP_RESTORE("备份与还原", "导入、导出快捷文本及其它兼容格式"),
    CAMERA("拍照", "相机与照片导入"),
    PHOTO_WEATHER("天气设置", "位置与天气数据源"),
    ABOUT("关于", "版本、协议与隐私"),
    USER_AGREEMENT("用户协议", ""),
    PRIVACY_POLICY("隐私政策", ""),
    OPEN_SOURCE_LICENSES("开源许可证", ""),
}
