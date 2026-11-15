package com.kigtts.next.sharing

internal enum class ProfileShareFormat(val label: String, val fileName: String, val mime: String) {
    PDF("PDF", "个人主页.pdf", "application/pdf"),
    MHTML("离线网页", "个人主页.mhtml", com.kigtts.next.identity.ProfileArchive.MIME),
    LONG_IMAGE("长图", "个人主页.png", "image/png")
}
