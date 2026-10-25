package com.kigtts.next.identity

internal val SocialPlatform.displayName: String get() = when (this) {
    SocialPlatform.QQ -> "QQ"; SocialPlatform.X -> "X"; SocialPlatform.GITHUB -> "GitHub"; SocialPlatform.YOUTUBE -> "YouTube"; SocialPlatform.TELEGRAM -> "Telegram"; SocialPlatform.DISCORD -> "Discord"; SocialPlatform.INSTAGRAM -> "Instagram"; SocialPlatform.FACEBOOK -> "Facebook"; SocialPlatform.LINE -> "LINE"; SocialPlatform.WHATSAPP -> "WhatsApp"; SocialPlatform.LINK -> "自定义链接"
    SocialPlatform.EMAIL -> "邮箱"
    else -> labels.first()
}

/** Keep imported platform QR payloads intact; an account alone remains an account QR. */
internal fun contactPayload(link: ContactLink): String = link.qrContent?.takeIf(String::isNotBlank)
    ?: if (socialPlatform(link) == SocialPlatform.EMAIL && '@' in link.value && !link.value.startsWith("mailto:", true)) "mailto:${link.value.trim()}"
    else link.value.trim()
