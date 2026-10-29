package com.kigtts.next.identity

import androidx.annotation.DrawableRes
import com.kigtts.next.R
import java.net.URI
import java.util.Locale

/** Bundled Remix vectors; services without a Remix brand mark use a neutral category glyph. */
internal enum class SocialPlatform(@DrawableRes val icon: Int, val labels: Set<String>, val hosts: Set<String> = emptySet()) {
    WECHAT(R.drawable.ic_remix_wechat_fill, setOf("微信", "微信号", "wechat", "weixin"), setOf("weixin.qq.com", "wechat.com")),
    QQ(R.drawable.ic_remix_qq_fill, setOf("qq", "qq号", "qq群", "腾讯qq"), setOf("qm.qq.com", "wpa.qq.com", "qun.qq.com", "qzone.qq.com")),
    WEIBO(R.drawable.ic_remix_weibo_fill, setOf("微博", "新浪微博", "weibo"), setOf("weibo.com", "weibo.cn")),
    BILIBILI(R.drawable.ic_remix_bilibili_fill, setOf("哔哩哔哩", "b站", "bilibili"), setOf("bilibili.com", "b23.tv")),
    DOUYIN(R.drawable.ic_remix_tiktok_fill, setOf("抖音", "douyin"), setOf("douyin.com")),
    TIKTOK(R.drawable.ic_remix_tiktok_fill, setOf("TikTok"), setOf("tiktok.com")),
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
    XIAOHONGSHU(R.drawable.ic_remix_book_2_fill, setOf("小红书", "xhs", "rednote", "xiaohongshu"), setOf("xiaohongshu.com", "xhslink.com")),
    KUAISHOU(R.drawable.ic_remix_film_fill, setOf("快手", "kuaishou"), setOf("kuaishou.com", "gifshow.com")),
    DOUBAN(R.drawable.ic_remix_douban_fill, setOf("豆瓣", "douban"), setOf("douban.com")),
    JIKE(R.drawable.ic_remix_flashlight_fill, setOf("即刻", "jike", "jellow"), setOf("okjike.com", "jellow.site")),
    TIEBA(R.drawable.ic_remix_baidu_fill, setOf("百度贴吧", "贴吧", "tieba"), setOf("tieba.baidu.com")),
    QZONE(R.drawable.ic_remix_qq_fill, setOf("QQ空间", "空间", "qzone"), setOf("qzone.qq.com")),
    WECHAT_OFFICIAL(R.drawable.ic_remix_wechat_2_fill, setOf("微信公众号", "公众号", "订阅号"), setOf("mp.weixin.qq.com")),
    WECHAT_CHANNELS(R.drawable.ic_remix_wechat_channels_fill, setOf("微信视频号", "视频号"), setOf("channels.weixin.qq.com")),
    NETEASE_MUSIC(R.drawable.ic_remix_netease_cloud_music_fill, setOf("网易云音乐", "网易云", "netease", "cloudmusic"), setOf("music.163.com", "163cn.tv")),
    QQ_MUSIC(R.drawable.ic_remix_music_2_fill, setOf("QQ音乐", "qqmusic"), setOf("y.qq.com")),
    XIAOYUZHOU(R.drawable.ic_remix_mic_fill, setOf("小宇宙", "xiaoyuzhou", "播客"), setOf("xiaoyuzhoufm.com")),
    XIMALAYA(R.drawable.ic_remix_headphone_fill, setOf("喜马拉雅", "ximalaya", "喜马"), setOf("ximalaya.com")),
    SPOTIFY(R.drawable.ic_remix_spotify_fill, setOf("Spotify"), setOf("spotify.com", "spotify.link")),
    APPLE_MUSIC(R.drawable.ic_remix_music_2_fill, setOf("Apple Music", "苹果音乐"), setOf("music.apple.com")),
    APPLE_PODCASTS(R.drawable.ic_remix_mic_fill, setOf("Apple Podcasts", "苹果播客"), setOf("podcasts.apple.com")),
    SOUNDCLOUD(R.drawable.ic_remix_soundcloud_fill, setOf("SoundCloud"), setOf("soundcloud.com")),
    TWITCH(R.drawable.ic_remix_twitch_fill, setOf("Twitch"), setOf("twitch.tv")),
    VIMEO(R.drawable.ic_remix_vimeo_fill, setOf("Vimeo"), setOf("vimeo.com")),
    JIANSHU(R.drawable.ic_remix_article_fill, setOf("简书", "jianshu"), setOf("jianshu.com")),
    JUEJIN(R.drawable.ic_remix_code_s_slash_fill, setOf("掘金", "juejin"), setOf("juejin.cn", "juejin.im")),
    CSDN(R.drawable.ic_remix_code_s_slash_fill, setOf("CSDN"), setOf("csdn.net")),
    CNBLOGS(R.drawable.ic_remix_code_s_slash_fill, setOf("博客园", "cnblogs"), setOf("cnblogs.com")),
    YUQUE(R.drawable.ic_remix_yuque_fill, setOf("语雀", "yuque"), setOf("yuque.com")),
    MEDIUM(R.drawable.ic_remix_medium_fill, setOf("Medium"), setOf("medium.com")),
    TUMBLR(R.drawable.ic_remix_tumblr_fill, setOf("Tumblr"), setOf("tumblr.com")),
    WORDPRESS(R.drawable.ic_remix_wordpress_fill, setOf("WordPress"), setOf("wordpress.com")),
    LOFTER(R.drawable.ic_remix_image_fill, setOf("LOFTER", "乐乎", "老福特"), setOf("lofter.com")),
    GITLAB(R.drawable.ic_remix_gitlab_fill, setOf("GitLab"), setOf("gitlab.com")),
    GITEE(R.drawable.ic_remix_gitee_fill, setOf("Gitee", "码云"), setOf("gitee.com")),
    STACK_OVERFLOW(R.drawable.ic_remix_stack_overflow_fill, setOf("Stack Overflow"), setOf("stackoverflow.com")),
    CODEPEN(R.drawable.ic_remix_codepen_fill, setOf("CodePen"), setOf("codepen.io")),
    HUGGING_FACE(R.drawable.ic_remix_user_star_fill, setOf("Hugging Face", "hf", "抱抱脸"), setOf("huggingface.co")),
    PRODUCT_HUNT(R.drawable.ic_remix_product_hunt_fill, setOf("Product Hunt"), setOf("producthunt.com")),
    NOTION(R.drawable.ic_remix_notion_fill, setOf("Notion"), setOf("notion.so", "notion.site")),
    ZCOOL(R.drawable.ic_remix_zcool_fill, setOf("站酷", "zcool"), setOf("zcool.com.cn")),
    FIGMA(R.drawable.ic_remix_figma_fill, setOf("Figma"), setOf("figma.com")),
    DRIBBBLE(R.drawable.ic_remix_dribbble_fill, setOf("Dribbble"), setOf("dribbble.com")),
    BEHANCE(R.drawable.ic_remix_behance_fill, setOf("Behance"), setOf("behance.net")),
    PINTEREST(R.drawable.ic_remix_pinterest_fill, setOf("Pinterest"), setOf("pinterest.com", "pin.it")),
    PIXIV(R.drawable.ic_remix_brush_fill, setOf("Pixiv"), setOf("pixiv.net")),
    ARTSTATION(R.drawable.ic_remix_image_fill, setOf("ArtStation"), setOf("artstation.com")),
    UNSPLASH(R.drawable.ic_remix_unsplash_fill, setOf("Unsplash"), setOf("unsplash.com")),
    FLICKR(R.drawable.ic_remix_flickr_fill, setOf("Flickr"), setOf("flickr.com", "flic.kr")),
    AFDIAN(R.drawable.ic_remix_hand_heart_fill, setOf("爱发电", "afdian"), setOf("afdian.com", "afdian.net")),
    BUY_ME_A_COFFEE(R.drawable.ic_remix_cup_fill, setOf("Buy Me a Coffee", "buymeacoffee", "bmac"), setOf("buymeacoffee.com")),
    KOFI(R.drawable.ic_remix_cup_fill, setOf("Ko-fi", "kofi"), setOf("ko-fi.com")),
    PATREON(R.drawable.ic_remix_patreon_fill, setOf("Patreon"), setOf("patreon.com")),
    PAYPAL(R.drawable.ic_remix_paypal_fill, setOf("PayPal"), setOf("paypal.com", "paypal.me")),
    EMAIL(R.drawable.ic_remix_mail_line, setOf("邮箱", "邮件", "电子邮件", "email", "e-mail", "mail")),
    LINK(R.drawable.ic_remix_links_line, setOf("自定义链接", "链接", "link", "url"));
}

internal fun socialPlatform(link: ContactLink): SocialPlatform {
    val label = socialSearchKey(link.label)
    SocialPlatform.entries.firstOrNull { it.labels.any { name -> label == socialSearchKey(name) } }?.let { return it }
    val uri = runCatching { URI(link.value.trim()) }.getOrNull()
    if (uri?.scheme.equals("mailto", true)) return SocialPlatform.EMAIL
    val host = uri?.takeIf { it.scheme.equals("https", true) || it.scheme.equals("http", true) }?.host?.lowercase(Locale.ROOT)
    if (host == null) return SocialPlatform.LINK
    return SocialPlatform.entries.flatMap { platform -> platform.hosts.filter { host == it || host.endsWith(".$it") }.map { platform to it.length } }
        .maxByOrNull { it.second }?.first ?: SocialPlatform.LINK
}

internal fun socialSearchKey(value: String) = value.lowercase(Locale.ROOT).filterNot(Char::isWhitespace)
