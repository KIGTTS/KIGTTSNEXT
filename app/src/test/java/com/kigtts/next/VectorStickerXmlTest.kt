package com.kigtts.next

import com.kigtts.next.cards.generators.VectorStickerXml
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class VectorStickerXmlTest {
    private fun androidFactory() = Class.forName("org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl")
        .getDeclaredConstructor().newInstance() as DocumentBuilderFactory
    @Test fun platformParserAcceptsSafeSvgWithoutXercesFeatures() {
        val document = VectorStickerXml.parse("""<svg xmlns="http://www.w3.org/2000/svg"><text>你好 &amp; KIGTTS</text></svg>""", androidFactory())
        assertEquals("你好 & KIGTTS", document.getElementsByTagName("text").item(0).textContent)
    }
    @Test fun platformParserRejectsExternalAndInternalEntityDeclarations() {
        for (xml in listOf("<!DOCTYPE svg SYSTEM 'https://example.invalid/evil'><svg/>",
            "<!DOCTYPE svg [<!ENTITY x 'expand'>]><svg>&x;</svg>",
            "<?xml-stylesheet href='https://example.invalid/evil'?><svg/>")) {
            assertThrows(IllegalArgumentException::class.java) { VectorStickerXml.parse(xml, androidFactory()) }
        }
    }
}
