package com.kigtts.next.media

import android.content.Context
import com.kigtts.next.identity.writeProfileJpeg
import com.kigtts.next.state.AppState
import com.kigtts.next.state.Destination
import com.kigtts.next.theme.BackgroundBlurCache
import com.kigtts.next.theme.ResolvedAppearance
import com.kigtts.plugin.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.Locale
import java.util.UUID

internal data class WebPagePresentation(val owner: Any, val mediaId: String, val phraseId: String,
    val appearance: ResolvedAppearance, val chrome: HomeChromePresentation) {
    fun matches(state: AppState): Boolean = state.destination == Destination.Home && state.sidePage == null &&
        state.mediaId == mediaId && state.webPermissionEntryId == phraseId
}

internal class WebPresentationSession(private val context: Context, private val media: String,
    private val phrase: String, private val localRoot: File?, private val cleanupScope: CoroutineScope,
    private val publish: (Any, WebPagePresentation?) -> Unit) {
    private val owner = Any()
    private var generation = 0
    private var image: File? = null

    fun clear() {
        generation++
        publish(owner, null)
        val previous = image
        image = null
        previous?.let { discard(it) }
    }

    suspend fun apply(data: JSONObject, projectOrigin: Boolean, active: () -> Boolean, authorize: suspend () -> Boolean): Boolean {
        val appearance = if (data.has("appearance")) data.getJSONObject("appearance") else JSONObject()
        val controls = if (data.has("controls")) data.getJSONObject("controls") else JSONObject()
        val background = appearance.opt("background")
        val backgroundSource = when (background) {
            null, JSONObject.NULL -> null
            is String -> background.also { require(it.isNotBlank()) }
            else -> error("Invalid background")
        }
        val patch = AppearancePatch(
            themeColor = appearance.opt("themeColor")?.let(::color),
            mode = (appearance.opt("mode") as? String)?.let { AppearanceMode.valueOf(it.uppercase(Locale.ROOT)) },
            background = if (appearance.has("background")) AppearanceBackground.Clear else null,
            backgroundDim = (appearance.opt("backgroundDim") as? Number)?.toFloat(),
            pureBlackDark = appearance.opt("pureBlackDark") as? Boolean,
            contrast = (appearance.opt("contrast") as? String)?.let { AppearanceContrast.valueOf(it.uppercase(Locale.ROOT)) },
        )
        val chrome = HomeChromePresentation(controls.optBoolean("fadeHeaderWhenIdle"), controls.optBoolean("fadeDockWhenIdle"))
        val request = ++generation
        if (!authorize() || request != generation || !active()) return false
        var next: File? = null
        try {
            if (backgroundSource != null) withContext(Dispatchers.IO) {
                next = prepareBackground(backgroundSource, projectOrigin)
            }
            if (request != generation || !active()) return false
            val file = next
            val resolved = ResolvedAppearance(
                if (file == null) patch else patch.copy(background = AppearanceBackground.Image(PluginResource(file.name))),
                file?.name,
            )
            publish(owner, WebPagePresentation(owner, media, phrase, resolved, chrome))
            image?.let { discard(it) }
            image = file
            next = null
            return true
        } finally { next?.delete() }
    }

    private fun discard(file: File) {
        cleanupScope.launch(Dispatchers.IO) { BackgroundBlurCache(context).removeTemporary(file) }
    }

    private fun prepareBackground(value: String, projectOrigin: Boolean): File {
        val root = File(context.cacheDir, "web-presentation").apply { mkdirs() }
        val target = File(root, "web-${UUID.randomUUID()}.jpg")
        var staged: File? = null
        try {
            val source = if (value.startsWith("data:")) {
                val prefix = value.substringBefore(',')
                require(prefix in setOf("data:image/png;base64", "data:image/jpeg;base64", "data:image/webp;base64"))
                val data = Base64.getDecoder().decode(value.substringAfter(',', ""))
                require(data.size in 1..4 * 1024 * 1024)
                File.createTempFile("source-", ".img", root).also { staged = it; it.writeBytes(data) }
            } else {
                require(projectOrigin)
                requireNotNull(localRoot?.let { WebContentPolicy.file(it, value) })
                    .also { require(it.length() in 1..8L * 1024 * 1024) }
            }
            check(writeProfileJpeg(source, target))
            return target
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally { staged?.delete() }
    }

    private fun color(value: Any): Long = when (value) {
        is Number -> value.toLong().also { require(it in 0..0xffffffffL) }
        is String -> {
            require(value.matches(Regex("#[0-9a-fA-F]{6}")))
            0xff000000L or value.substring(1).toLong(16)
        }
        else -> error("Invalid theme color")
    }
}
