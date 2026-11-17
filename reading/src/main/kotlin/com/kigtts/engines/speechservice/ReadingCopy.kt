// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.api.PluginLocalization
import com.kigtts.plugin.api.PluginStrings
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object ReadingCopy {
    val localization = PluginLocalization(supportedLocales = listOf("zh-CN", "en", "ko", "ja"))
    private val catalog by lazy {
        val loader = requireNotNull(javaClass.classLoader)
        PluginStrings(localization.supportedLocales.associateWith { tag ->
            requireNotNull(loader.getResourceAsStream("localization/reading/$tag.json")).bufferedReader().use { reader ->
                PluginJson.parseToJsonElement(reader.readText()).jsonObject.mapValues { it.value.jsonPrimitive.content }
            }
        })
    }
    fun text(key: String, locale: String) = catalog.text(key, locale)
}
