package com.kigtts.next.identity

internal enum class SocialPlatformGroup(val title: String) {
    CONTACT("聊天与联系"), SOCIAL("社交媒体"), MEDIA("音视频创作"), WRITING("文字与图文"),
    DEVELOPMENT("开发者"), DESIGN("设计与摄影"), SUPPORT("捐赠与支持"), OTHER("其他")
}

internal val SocialPlatform.group: SocialPlatformGroup get() = when (this) {
    SocialPlatform.WECHAT, SocialPlatform.QQ, SocialPlatform.TELEGRAM, SocialPlatform.DISCORD,
    SocialPlatform.LINE, SocialPlatform.WHATSAPP, SocialPlatform.EMAIL -> SocialPlatformGroup.CONTACT
    SocialPlatform.WEIBO, SocialPlatform.X, SocialPlatform.TWITTER, SocialPlatform.FACEBOOK,
    SocialPlatform.INSTAGRAM, SocialPlatform.LINKEDIN, SocialPlatform.MASTODON, SocialPlatform.BLUESKY,
    SocialPlatform.THREADS, SocialPlatform.REDDIT, SocialPlatform.STEAM, SocialPlatform.SNAPCHAT,
    SocialPlatform.XIAOHONGSHU, SocialPlatform.DOUBAN, SocialPlatform.JIKE, SocialPlatform.TIEBA,
    SocialPlatform.QZONE -> SocialPlatformGroup.SOCIAL
    SocialPlatform.BILIBILI, SocialPlatform.DOUYIN, SocialPlatform.TIKTOK, SocialPlatform.YOUTUBE,
    SocialPlatform.KUAISHOU, SocialPlatform.WECHAT_CHANNELS, SocialPlatform.NETEASE_MUSIC,
    SocialPlatform.QQ_MUSIC, SocialPlatform.XIAOYUZHOU, SocialPlatform.XIMALAYA, SocialPlatform.SPOTIFY,
    SocialPlatform.APPLE_MUSIC, SocialPlatform.APPLE_PODCASTS, SocialPlatform.SOUNDCLOUD,
    SocialPlatform.TWITCH, SocialPlatform.VIMEO -> SocialPlatformGroup.MEDIA
    SocialPlatform.ZHIHU, SocialPlatform.WECHAT_OFFICIAL, SocialPlatform.JIANSHU, SocialPlatform.YUQUE,
    SocialPlatform.MEDIUM, SocialPlatform.TUMBLR, SocialPlatform.WORDPRESS, SocialPlatform.LOFTER,
    SocialPlatform.NOTION -> SocialPlatformGroup.WRITING
    SocialPlatform.GITHUB, SocialPlatform.JUEJIN, SocialPlatform.CSDN, SocialPlatform.CNBLOGS,
    SocialPlatform.GITLAB, SocialPlatform.GITEE, SocialPlatform.STACK_OVERFLOW, SocialPlatform.CODEPEN,
    SocialPlatform.HUGGING_FACE, SocialPlatform.PRODUCT_HUNT -> SocialPlatformGroup.DEVELOPMENT
    SocialPlatform.ZCOOL, SocialPlatform.FIGMA, SocialPlatform.DRIBBBLE, SocialPlatform.BEHANCE,
    SocialPlatform.PINTEREST, SocialPlatform.PIXIV, SocialPlatform.ARTSTATION,
    SocialPlatform.UNSPLASH, SocialPlatform.FLICKR -> SocialPlatformGroup.DESIGN
    SocialPlatform.AFDIAN, SocialPlatform.BUY_ME_A_COFFEE, SocialPlatform.KOFI,
    SocialPlatform.PATREON, SocialPlatform.PAYPAL -> SocialPlatformGroup.SUPPORT
    SocialPlatform.LINK -> SocialPlatformGroup.OTHER
}

internal fun socialPlatformGroups(query: String): Map<SocialPlatformGroup, List<SocialPlatform>> {
    val key = socialSearchKey(query)
    return SocialPlatformGroup.entries.mapNotNull { group ->
        val platforms = SocialPlatform.entries.filter { it.group == group && (key.isEmpty() ||
            sequenceOf(it.displayName, group.title).plus(it.labels.asSequence()).plus(it.hosts.asSequence())
                .any { name -> socialSearchKey(name).contains(key) }) }
        platforms.takeIf { it.isNotEmpty() }?.let { group to it }
    }.toMap()
}
