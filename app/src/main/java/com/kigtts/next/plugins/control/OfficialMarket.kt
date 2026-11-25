package com.kigtts.next.plugins.control

import android.content.Context
import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.api.PluginStrings
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val STORE_ID = "tools.package-store"
internal const val STORE_PUBLISHER = "b5927c76c21539ef99352707c215b3ce48f3fed937a6f995d39a3c677a052ab5"

internal fun marketStrings(context: Context): PluginStrings = PluginStrings(
    listOf("zh-CN", "zh-CN-x-casual", "en", "ja", "ko").associateWith { locale ->
        context.assets.open("market/i18n/$locale.json").bufferedReader(Charsets.UTF_8).use { reader ->
            PluginJson.parseToJsonElement(reader.readText()).jsonObject.mapValues { it.value.jsonPrimitive.content }
        }
    }
)
