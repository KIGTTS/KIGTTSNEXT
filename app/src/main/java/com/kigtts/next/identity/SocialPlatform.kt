package com.kigtts.next.identity

import androidx.annotation.DrawableRes
import com.kigtts.next.R
import java.net.URI
import java.util.Locale

/** Brand marks are bundled Remix Icon vectors. An unknown service keeps a neutral link icon. */
internal enum class SocialPlatform(@DrawableRes val icon: Int, val labels: Set<String>, val hosts: Set<String> = emptySet()) {
    WECHAT(R.drawable.ic_remix_wechat_fill, setOf("微信", "微信号", "wechat", "weixin"), setOf("weixin.qq.com", "wechat.com")),
    QQ(R.drawable.ic_remix_qq_fill, setOf("qq", "qq号", "qq群", "腾讯qq"), setOf("qm.qq.com", "wpa.qq.com", "qun.qq.com", "qzone.qq.com")),
    WEIBO(R.drawable.ic_remix_weibo_fill, setOf("微博", "新浪微博", "weibo"), setOf("weibo.com", "weibo.cn")),
    BILIBILI(R.drawable.ic_remix_bilibili_fill, setOf("哔哩哔哩", "b站", "bilibili"), setOf("bilibili.com", "b23.tv")),
    TIKTOK(R.drawable.ic_remix_tiktok_fill, setOf("抖音", "douyin", "tiktok"), setOf("douyin.com", "tiktok.com")),
    X(R.drawable.ic_remix_twitter_x_fill, setOf("x", "x/twitter"), setOf("x.com")),
    TWITTER(R.drawable.ic_remix_twitter_fill, setOf("推特", "twitter"), setOf("twitter.com")),
    TELEGRAM(R.drawable.ic_remix_telegram_fill, setOf("telegram", "tg", "电报"), setOf("t.me", "telegram.me", "telegram.org")),
    DISCORD(R.drawable.ic_remix_discord_fill, setOf("discord", "dc"), setOf("discord.com", "discord.gg")),
    FACEBOOK(R.drawable.ic_remix_facebook_fill, setOf("facebook", "fb", "脸书"), setOf("facebook.com", "fb.me")),
    INSTAGRAM(R.drawable.ic_remix_instagram_fill, setOf("instagram", "ins", "ig"), setOf("instagram.com")),
    YOUTUBE(R.drawable.ic_remix_youtube_fill, setOf("youtube", "yt", "油管"), setOf("youtube.com", "youtu.be")),
    GITHUB(R.drawable.ic_remix_github_fill, setOf("github"), setOf("github.com")),
    LINE(R.drawable.ic_remix_line_fill, setOf("line"), setOf("line.me")),
    WHATSAPP(R.drawable.ic_remix_whatsapp_fill, setOf("whatsapp", "wa"), setOf("whatsapp.com", "wa.me")),
    ZHIHU(R.drawable.ic_remix_zhihu_fill, setOf("知乎", "zhihu"), setOf("zhihu.com")),
    LINKEDIN(R.drawable.ic_remix_linkedin_fill, setOf("linkedin", "领英"), setOf("linkedin.com")),
    MASTODON(R.drawable.ic_remix_mastodon_fill, setOf("mastodon", "长毛象", "猛犸")),
    BLUESKY(R.drawable.ic_remix_bluesky_fill, setOf("bluesky", "bsky", "蓝天"), setOf("bsky.app")),
    THREADS(R.drawable.ic_remix_threads_fill, setOf("threads"), setOf("threads.net", "threads.com")),
    REDDIT(R.drawable.ic_remix_reddit_fill, setOf("reddit"), setOf("reddit.com")),
    STEAM(R.drawable.ic_remix_steam_fill, setOf("steam"), setOf("steamcommunity.com", "steampowered.com")),
    SNAPCHAT(R.drawable.ic_remix_snapchat_fill, setOf("snapchat"), setOf("snapchat.com")),
    EMAIL(R.drawable.ic_remix_mail_line, setOf("邮箱", "邮件", "电子邮件", "email", "e-mail", "mail")),
    LINK(R.drawable.ic_remix_links_line, emptySet());
}

internal fun socialPlatform(link: ContactLink): SocialPlatform {
    val label = link.label.trim().lowercase(Locale.ROOT).replace(" ", "")
    SocialPlatform.entries.firstOrNull { label in it.labels }?.let { return it }
    val uri = runCatching { URI(link.value.trim()) }.getOrNull()
    if (uri?.scheme.equals("mailto", true)) return SocialPlatform.EMAIL
    val host = uri?.takeIf { it.scheme.equals("https", true) || it.scheme.equals("http", true) }?.host?.lowercase(Locale.ROOT)
    return SocialPlatform.entries.firstOrNull { platform -> host != null && platform.hosts.any { host == it || host.endsWith(".$it") } }
        ?: SocialPlatform.LINK
}
