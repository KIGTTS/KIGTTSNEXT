package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.api.PluginLocalization
import com.kigtts.plugin.api.PluginStrings
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object RecognitionCopy {
    val localization = PluginLocalization(supportedLocales = listOf("zh-CN", "en", "ko"))
    private val catalog by lazy {
        val loader = requireNotNull(javaClass.classLoader)
        PluginStrings(localization.supportedLocales.associateWith { tag ->
            requireNotNull(loader.getResourceAsStream("localization/recognition/$tag.json")).bufferedReader().use { reader ->
                PluginJson.parseToJsonElement(reader.readText()).jsonObject.mapValues { it.value.jsonPrimitive.content }
            }
        })
    }
    fun text(key: String, locale: String) = catalog.text(key, locale)
}
