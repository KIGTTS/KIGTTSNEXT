package com.kigtts.next.state

/** Stable keys are persisted independently of group names and ordering. */
enum class CategoryIcon(val label: String) {
    LIST("列表"), INFO("常用"), PERSON("生活"), FAVORITE("收藏"), HOME("家庭"),
    WORK("工作"), SCHOOL("学习"), TRAVEL("出行"), FOOD("餐饮"), HEALTH("健康"),
    SHOPPING("购物"), MESSAGE("交流");

    companion object {
        fun defaultFor(category: String) = when (category) {
            "常用" -> INFO
            "生活" -> PERSON
            "收藏" -> FAVORITE
            else -> LIST
        }
    }
}
