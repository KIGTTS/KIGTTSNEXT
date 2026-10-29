package com.kigtts.next.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialPlatformTest {
    @Test fun platformNamesRoundTripAndCommonChineseAndInternationalAccountsAreSearchable() {
        SocialPlatform.entries.forEach { platform ->
            assertEquals(platform.name, platform, socialPlatform(ContactLink(platform.displayName, "account")))
        }
        assertEquals(SocialPlatform.DOUYIN, socialPlatform(ContactLink("抖音", "account")))
        assertEquals(SocialPlatform.TIKTOK, socialPlatform(ContactLink("TikTok", "account")))
        assertEquals(SocialPlatform.XIAOHONGSHU, socialPlatform(ContactLink("rednote", "account")))
        assertEquals(SocialPlatform.STACK_OVERFLOW, socialPlatform(ContactLink(" STACK OVERFLOW ", "account")))
        assertEquals(listOf(SocialPlatform.XIAOHONGSHU), socialPlatformGroups("小红书").values.flatten())
        assertEquals(listOf(SocialPlatform.APPLE_MUSIC), socialPlatformGroups(" apple  music ").values.flatten())
        assertTrue(socialPlatformGroups("音乐").values.flatten().containsAll(listOf(SocialPlatform.NETEASE_MUSIC, SocialPlatform.QQ_MUSIC)))
        assertTrue(socialPlatformGroups("设计与摄影").values.flatten().containsAll(listOf(SocialPlatform.FIGMA, SocialPlatform.PIXIV)))
        assertTrue(socialPlatformGroups("不存在的平台名称").isEmpty())
    }

    @Test fun platformDomainMatchingUsesBoundariesAndPrefersTheMostSpecificService() {
        assertEquals(SocialPlatform.XIAOHONGSHU, socialPlatform(ContactLink("主页", "https://www.xiaohongshu.com/user/profile/a")))
        assertEquals(SocialPlatform.WECHAT_OFFICIAL, socialPlatform(ContactLink("主页", "https://mp.weixin.qq.com/s/a")))
        assertEquals(SocialPlatform.WECHAT_CHANNELS, socialPlatform(ContactLink("主页", "https://channels.weixin.qq.com/a")))
        assertEquals(SocialPlatform.APPLE_PODCASTS, socialPlatform(ContactLink("主页", "https://podcasts.apple.com/cn/podcast/a")))
        assertEquals(SocialPlatform.LINK, socialPlatform(ContactLink("主页", "https://xiaohongshu.com.example.org/a")))
        assertEquals(SocialPlatform.LINK, socialPlatform(ContactLink("主页", "https://example.org/?url=https://ko-fi.com/a")))
    }

    @Test fun serviceLabelsAndActualDomainsChooseTheSameBundledBrandMark() {
        assertEquals(SocialPlatform.WECHAT, socialPlatform(ContactLink("微信", "example")))
        assertEquals(SocialPlatform.QQ, socialPlatform(ContactLink(" QQ ", "123456")))
        assertEquals(SocialPlatform.BILIBILI, socialPlatform(ContactLink("我的主页", "https://space.bilibili.com/123")))
        assertEquals(SocialPlatform.TELEGRAM, socialPlatform(ContactLink("找我", "https://t.me/example")))
        assertEquals(SocialPlatform.EMAIL, socialPlatform(ContactLink("联系", "mailto:hello@example.com")))
        assertEquals(SocialPlatform.LINK, socialPlatform(ContactLink("主页", "https://github.com.example.org/")))
        assertEquals(SocialPlatform.LINK, socialPlatform(ContactLink("主页", "https://example.com/github.com")))
    }
}
