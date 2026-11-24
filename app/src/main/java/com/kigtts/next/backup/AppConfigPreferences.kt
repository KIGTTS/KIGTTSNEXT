package com.kigtts.next.backup

import android.content.Context
import android.content.SharedPreferences
import com.kigtts.next.i18n.UI_LANGUAGE_PREFERENCES
import com.kigtts.next.legacy.legacyJson
import com.kigtts.plugin.runtime.PluginPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal typealias ConfigValues = Map<String, Map<String, Any>>

/** The wire schema excludes permissions, sessions, secrets, and content catalogues. */
internal class AppConfigPreferences(private val context: Context) {
    private enum class Kind { TEXT, BOOL, INT, LONG, FLOAT, LIST }

    private fun fields(text: String = "", bool: String = "", int: String = "", long: String = "",
        float: String = "") = buildMap {
        listOf(text to Kind.TEXT, bool to Kind.BOOL, int to Kind.INT, long to Kind.LONG, float to Kind.FLOAT)
            .forEach { (keys, type) -> keys.split(' ').filter(String::isNotEmpty).forEach { put(it, type) } }
    }

    private val schema = mapOf(
        "appearance" to fields(text = "mode background font fontName subtitlePlaceholder",
            bool = "deeperDark wallpaper backgroundColor askBackgroundColor secondaryPageBackground",
            int = "fontWeight", long = "seed backgroundSeed", float = "contrast radius backgroundDim subtitleWidthPercent"),
        UI_LANGUAGE_PREFERENCES to fields(text = "locale"),
        "interaction" to fields(bool = "haptics quick-sort double-back-exit"),
        "subtitle-appearance" to fields(text = "alignment", bool = "bold rich-text-editing reduced-motion display-reflow"),
        "caption-window" to fields(float = "panel-font-size font-size"),
        "home-panel" to fields(int = "expanded-columns", float = "expanded-height"),
        "engine-selection" to fields(text = "tts asr"),
        "playback" to fields(bool = "automatic"),
        "experiments" to fields(bool = "apple-live-photos"),
        "media-presentation" to fields(text = "audioMode", bool = "autoPlay"),
        "speech-audio-devices" to fields(text = "input output input-name output-name",
            bool = "input-noise-suppression", float = "output-gain"),
        "native-overlay" to fields(text = "settings"),
        "management-order" to fields(text = "plugins"),
        "polaroid_camera" to fields(text = "grid flash processing aspect photo-encoding watermark-asset-v1",
            bool = "level exposure-warning motion video-stabilization watermark-enabled-v1 use-system-camera",
            int = "timer", float = "filter-strength effect-vignette effect-grain effect-aberration effect-bloom"),
        "appearance-fonts" to emptyMap(),
        "reading-service" to fields(text = "provider voice systemVoice systemLanguage", bool = "resident cacheEnabled",
            int = "idleMinutes", float = "rate"),
        "recognition-service" to (fields(text = "provider", bool = "routingDisabled resident voiceInput continuousInput",
            int = "idleSeconds") + ("order" to Kind.LIST)),
    )
    private val services by lazy {
        val paths = PluginPaths(context)
        mapOf(
            "reading-service" to paths.storage("service.speech-synthesis").file("config/service.json"),
            "recognition-service" to paths.storage("service.speech-recognition").file("config/recognition-service.json"),
        )
    }

    private fun kind(section: String, key: String): Kind? = schema[section]?.get(key) ?: when {
        section == "appearance-fonts" && fontKey.matches(key) -> Kind.TEXT
        section == "polaroid_camera" && watermarkKey.matches(key) -> Kind.TEXT
        else -> null
    }

    fun snapshot(): ConfigValues = schema.keys.associateWith { section ->
        read(section).mapNotNull { (key, value) ->
            kind(section, key)?.let { type -> value?.let { key to normalize(section, key, type, it) } }
        }.toMap()
    }

    fun decode(root: JSONObject): ConfigValues = buildMap {
        require(root.length() <= 32)
        root.keys().asSequence().filter { it in schema }.forEach { section ->
            val fields = root.getJSONObject(section)
            require(fields.length() <= 256)
            put(section, fields.keys().asSequence().mapNotNull { key ->
                kind(section, key)?.let { key to normalize(section, key, it, fields.get(key)) }
            }.toMap())
        }
    }

    @Synchronized fun apply(values: ConfigValues, replace: Boolean) {
        val saved = schema.keys.associateWith { section ->
            read(section).filterKeys { kind(section, it) != null }.mapNotNull { (key, value) ->
                value?.let { key to it }
            }.toMap()
        }
        val changes = decode(encode(values))
        val touched = mutableListOf<String>()
        try {
            changes.forEach { (section, fields) ->
                touched += section
                write(section, fields, replace)
            }
        } catch (error: Exception) {
            touched.asReversed().forEach { section ->
                runCatching { write(section, saved.getValue(section), replace = true) }
                    .exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    private fun write(section: String, values: Map<String, Any>, replace: Boolean) {
        services[section]?.let { service ->
            val settings = JSONObject(read(section))
            if (replace) settings.keys().asSequence().toList().filter { kind(section, it) != null }.forEach(settings::remove)
            values.forEach { (key, value) -> settings.put(key, value) }
            writeService(service, settings)
            return
        }
        val prefs = preferences(section)
        val editor = prefs.edit()
        if (replace) prefs.all.keys.filter { kind(section, it) != null }.forEach(editor::remove)
        values.forEach { (key, value) -> editor.putValue(key, value) }
        check(editor.commit())
    }

    private fun preferences(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun read(section: String): Map<String, *> {
        val service = services[section] ?: return preferences(section).all
        if (!service.isFile) return emptyMap<String, Any>()
        require(service.length() <= 512 * 1024)
        val json = legacyJson(service.readText(Charsets.UTF_8))
        return json.keys().asSequence().filterNot(json::isNull).associateWith(json::get)
    }

    private fun writeService(file: File, settings: JSONObject) {
        file.parentFile!!.mkdirs()
        val temporary = File.createTempFile("settings-", ".tmp", file.parentFile)
        try {
            temporary.writeText(settings.toString(), Charsets.UTF_8)
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    private fun normalize(section: String, key: String, kind: Kind, value: Any): Any = when (kind) {
        Kind.TEXT -> {
            val text = (value as? String)?.takeIf { it.length <= if (key == "settings") 256 * 1024 else 8192 }
                ?: error("Invalid setting $section/$key")
            if (section == "appearance" && key in setOf("background", "font")) {
                require(if (key == "font") fontKey.matches(text) else backgroundKey.matches(text))
            }
            if (section == "native-overlay") com.kigtts.next.overlay.portableOverlaySettings(text) else text
        }
        Kind.BOOL -> value as? Boolean ?: error("Invalid setting $section/$key")
        Kind.INT -> integer(value).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
        Kind.LONG -> integer(value)
        Kind.FLOAT -> (value as? Number)?.toFloat()?.takeIf(Float::isFinite) ?: error("Invalid setting $section/$key")
        Kind.LIST -> (value as JSONArray).also { list ->
            require(list.length() <= 512 && (0 until list.length()).all { ((list.get(it) as? String)?.length ?: 0) in 1..160 })
        }
    }

    private fun integer(value: Any): Long = when (value) {
        is Int -> value.toLong()
        is Long -> value
        else -> error("Invalid integer setting")
    }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
        }
    }

    companion object {
        val fontKey = Regex("[a-f0-9]{64}\\.(ttf|otf)")
        val backgroundKey = Regex("[a-f0-9]{64}\\.jpg")
        private val watermarkKey = Regex("watermark-config-[A-Za-z0-9._-]{1,192}")
        fun encode(values: ConfigValues) = JSONObject().apply {
            values.forEach { (section, fields) -> put(section, JSONObject(fields)) }
        }

        fun assets(values: ConfigValues): Set<String> = buildSet {
            addAll(values["appearance-fonts"].orEmpty().keys)
            values["appearance"].orEmpty().let {
                (it["font"] as? String)?.let(::add)
                (it["background"] as? String)?.let(::add)
            }
        }
    }
}
