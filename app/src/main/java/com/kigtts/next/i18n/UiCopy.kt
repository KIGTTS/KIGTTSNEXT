package com.kigtts.next.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.kigtts.plugin.api.PluginStrings
import org.json.JSONObject

internal enum class UiLanguage(val tag: String, val label: String) {
    STANDARD("zh-CN", "简体中文"),
    CASUAL("zh-CN-x-casual", "简体中文（轻松版）"),
    ENGLISH("en", "English"),
    KOREAN("ko", "한국어");

    companion object {
        fun fromTag(tag: String?): UiLanguage {
            val base = tag?.substringBefore('-')
            return entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
                ?: entries.firstOrNull { it.tag.equals(base, ignoreCase = true) }
                ?: STANDARD
        }
    }
}

internal class UiLanguageStore(context: Context) {
    private val preferences = context.getSharedPreferences("ui-language", Context.MODE_PRIVATE)
    fun load() = UiLanguage.fromTag(preferences.getString("locale", null)).tag
    fun save(language: UiLanguage) { preferences.edit().putString("locale", language.tag).apply() }
}

internal val LocalUiLocale = staticCompositionLocalOf { UiLanguage.STANDARD.tag }

private object AppCopyCatalog {
    @Volatile private var loaded: PluginStrings? = null
    fun get(context: Context): PluginStrings = loaded ?: synchronized(this) {
        loaded ?: PluginStrings(UiLanguage.entries.associate { language ->
            language.tag to context.assets.open("i18n/${language.tag}.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val json = JSONObject(reader.readText())
                json.keys().asSequence().associateWith(json::getString)
            }
        }).also { loaded = it }
    }
}

internal fun Context.uiCopy(key: String, vararg values: Pair<String, String>, locale: String = UiLanguageStore(this).load()) =
    AppCopyCatalog.get(this).text(key, locale, values.toMap())

@Composable internal fun uiText(key: String, vararg values: Pair<String, String>) =
    LocalContext.current.uiCopy(key, *values, locale = LocalUiLocale.current)
