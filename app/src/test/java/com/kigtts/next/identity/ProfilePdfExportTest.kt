package com.kigtts.next.identity

import com.kigtts.next.CardPrintCoordinatorTest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfilePdfExportTest {
    @Test fun offlineHomepageIncludesClickableContactAndImageAppendices() = runBlocking {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val person = IdentityRepository(f.app, f.cards).create().copy(nickname = "你好，新朋友", bio = "很高兴认识你。一起聊聊照片和软件。",
                tags = listOf("摄影", "开发"), contacts = listOf(ContactLink("网站", "https://lhtstudio.com")),
                tiles = listOf(ProfileTile(kind = ProfileTileKind.IMAGE, title = "图片预览", image = f.stickers.importResource(f.photo()).id),
                    ProfileTile(title = "介绍", text = "这是一段介绍。".repeat(200))))
            val output = File("build/previews/profile-pdf").apply { mkdirs() }
            val pdf = ProfilePdfExport.write(person, f.stickers, output)
            val raw = pdf.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(raw.startsWith("%PDF-1.4")); assertTrue(raw.contains("/Subtype /Link"))
            assertTrue(raw.contains("/GoTo")); assertTrue(raw.contains("/URI"))
            assertTrue(pdf.length() > 10000)
        } finally { f.scope.cancel() }
    }
}
