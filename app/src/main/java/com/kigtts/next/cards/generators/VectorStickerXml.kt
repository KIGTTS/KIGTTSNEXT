package com.kigtts.next.cards.generators

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.xml.sax.SAXException

/** Android Harmony does not implement Xerces feature flags. Validate before parsing on every platform. */
internal object VectorStickerXml {
    private val declarations = Regex("<!DOCTYPE|<!ENTITY|<\\?xml-stylesheet", RegexOption.IGNORE_CASE)
    fun parse(text: String, factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()): Document {
        require(text.length <= 2 * 1024 * 1024 && !declarations.containsMatchIn(text)) { "贴纸不支持文档声明或外部资源" }
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("贴纸不支持外部资源") }
        }.parse(text.byteInputStream())
    }
}
