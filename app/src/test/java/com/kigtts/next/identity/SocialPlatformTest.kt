package com.kigtts.next.identity

import org.junit.Assert.assertEquals
import org.junit.Test

class SocialPlatformTest {
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
