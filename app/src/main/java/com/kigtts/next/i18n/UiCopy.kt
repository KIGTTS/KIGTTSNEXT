package com.kigtts.next.i18n

import android.content.Context
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.kigtts.plugin.api.PluginStrings
import org.json.JSONObject

internal enum class UiLanguage(val tag: String, val label: String) {
    SYSTEM("system", ""),
    STANDARD("zh-CN", "简体中文"),
    CASUAL("zh-CN-x-casual", "简体中文（轻松版）"),
    ENGLISH("en", "English"),
    KOREAN("ko", "한국어"),
    JAPANESE("ja", "日本語");

    companion object {
        fun fromTag(tag: String?): UiLanguage = match(tag) ?: STANDARD

        fun fromSystem(locales: LocaleList): UiLanguage {
            for (index in 0 until locales.size()) {
                val language = match(locales[index].toLanguageTag())
                if (language != null && language != SYSTEM) return language
            }
            return STANDARD
        }

        private fun match(tag: String?): UiLanguage? {
            val base = tag?.substringBefore('-')
            return entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
                ?: entries.firstOrNull { it.tag.equals(base, ignoreCase = true) }
                ?: STANDARD.takeIf { base.equals("zh", ignoreCase = true) }
        }
    }
}

internal const val UI_LANGUAGE_PREFERENCES = "ui-language"

internal class UiLanguageStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(UI_LANGUAGE_PREFERENCES, Context.MODE_PRIVATE)
    fun selection() = preferences.getString("locale", null)?.let(UiLanguage::fromTag) ?: UiLanguage.SYSTEM
    fun load(selection: UiLanguage = selection()): String = when (selection) {
        UiLanguage.SYSTEM -> UiLanguage.fromSystem(context.resources.configuration.locales).tag
        else -> selection.tag
    }
    fun save(language: UiLanguage) { preferences.edit().putString("locale", language.tag).apply() }
}

internal val LocalUiLocale = staticCompositionLocalOf { UiLanguage.STANDARD.tag }

private object AppCopyCatalog {
    @Volatile private var loaded: PluginStrings? = null
    fun get(context: Context): PluginStrings = loaded ?: synchronized(this) {
        loaded ?: PluginStrings(UiLanguage.entries.filterNot { it == UiLanguage.SYSTEM }.associate { language ->
            language.tag to context.assets.open("i18n/${language.tag}.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val json = JSONObject(reader.readText())
                json.keys().asSequence().associateWith(json::getString)
            }
        }).also { loaded = it }
    }
}

internal fun Context.uiCopy(key: String, vararg values: Pair<String, String>, locale: String = UiLanguageStore(this).load()) =
    AppCopyCatalog.get(this).text(key, locale, values.toMap())

internal class UiCopy(private val catalog: PluginStrings, private val locale: String) {
    operator fun invoke(key: String, vararg values: Pair<String, String>): String = catalog.text(key, locale, values.toMap())
}

/** For Compose callbacks and accessibility lambdas which cannot call a composable lookup. */
@Composable internal fun rememberUiCopy(): UiCopy {
    val context = LocalContext.current
    val locale = LocalUiLocale.current
    return remember(context, locale) { UiCopy(AppCopyCatalog.get(context), locale) }
}

@Composable internal fun uiText(key: String, vararg values: Pair<String, String>) =
    rememberUiCopy().let { copy ->
        // Most controls keep the same caption through palette and navigation animations.
        if (values.isEmpty()) remember(copy, key) { copy(key) }
        else remember(copy, key, *values) { copy(key, *values) }
    }
