package com.kigtts.next.camera.expressions

import android.content.Context
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.theme.AppearanceAssets
import com.kigtts.plugin.api.PluginJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal class ExpressionLibrary private constructor(private val app: Context) {
    private val directory = File(app.filesDir, "camera-expressions")
    private val changes = MutableStateFlow(0)
    val revision = changes.asStateFlow()
    private val names = listOf("agni", "side_eye", "kim", "lau")

    fun choices(): List<ExpressionEffect> = names.mapNotNull(::builtIn) + directory.listFiles().orEmpty()
        .asSequence().filter { it.extension == "kigexpression" && it.length() in 1..ExpressionPack.MAX_PACK.toLong() }
        .sortedBy { it.name }.take(128)
        .flatMap { runCatching { read(it).effects }.getOrDefault(emptyList()) }.toList()

    fun find(id: String): ExpressionEffect? {
        if (id.startsWith("builtin.")) return builtIn(id.removePrefix("builtin."))
        if (!id.matches(Regex("[a-z0-9.-]{1,96}"))) return null
        val owner = if (id.startsWith("pack.")) id.split('.').take(2).joinToString(".") else id
        return runCatching { read(File(directory, "$owner.kigexpression")).effects.firstOrNull { it.id == id } }.getOrNull()
    }

    private fun builtIn(name: String): ExpressionEffect? {
        if (name !in names) return null
        val svg = app.assets.open("camera-expressions/$name.svg").use {
            AppearanceAssets.readBounded(it, ExpressionPack.MAX_SVG).toString(Charsets.UTF_8)
        }
        return ExpressionEffect("builtin.$name", app.uiCopy("expression.ink.$name"),
            listOf(ExpressionLayer(svg, ExpressionAnchor.EYES, scale = 1.32f)))
    }

    fun newDraft() = ExpressionEffect("local.${UUID.randomUUID()}", "", listOf(
        ExpressionLayer(DEFAULT_SPARKLE, ExpressionAnchor.FOREHEAD, scale = .65f, y = -.18f)
    ))

    fun editable(effect: ExpressionEffect): ExpressionEffect = if (effect.id.startsWith("local.")) effect
        else effect.copy(id = "local.${UUID.randomUUID()}")

    @Synchronized fun save(effect: ExpressionEffect) {
        require(effect.id.matches(Regex("local\\.[a-f0-9-]{36}")))
        val pack = ExpressionPack(effects = listOf(effect))
        CompiledExpression.compile(effect)
        write(effect.id, pack)
    }

    @Synchronized fun remove(id: String) {
        require(id.matches(Regex("local\\.[a-f0-9-]{36}")))
        File(directory, "$id.kigexpression").delete()
        changes.update { it + 1 }
    }

    @Synchronized fun install(source: File, packageId: String? = null): String {
        val pack = read(source)
        pack.effects.forEach(CompiledExpression::compile)
        val owner = packageId ?: com.kigtts.next.resources.ResourceArchive.hash(source)
        val namespace = "pack.${UUID.nameUUIDFromBytes(owner.toByteArray(Charsets.UTF_8))}"
        val installed = pack.copy(effects = pack.effects.map { effect ->
            effect.copy(id = "$namespace.${UUID.nameUUIDFromBytes(effect.id.toByteArray(Charsets.UTF_8))}")
        })
        write(namespace, installed)
        return installed.effects.first().id
    }

    fun share(effect: ExpressionEffect): android.content.Intent {
        val pack = ExpressionPack(effects = listOf(effect)).validate()
        val file = File.createTempFile("expression-", ".kigexpression", app.cacheDir)
        try {
            file.writeText(PluginJson.encodeToString(pack), Charsets.UTF_8)
            return com.kigtts.next.sharing.shareFileIntent(app, file, "${effect.name}.kigexpression", ExpressionPack.MIME)
        } finally { file.delete() }
    }

    private fun write(owner: String, pack: ExpressionPack) {
        val data = PluginJson.encodeToString(pack).toByteArray(Charsets.UTF_8)
        require(data.size <= ExpressionPack.MAX_PACK)
        directory.mkdirs()
        val file = File(directory, "$owner.kigexpression")
        val others = directory.listFiles().orEmpty().filter { it != file && it.extension == "kigexpression" }
        require(others.size < 48 && others.sumOf { it.length() } + data.size <= 16L * 1024 * 1024)
        val stage = File.createTempFile("expression-", ".tmp", directory)
        try {
            stage.writeBytes(data)
            try {
                Files.move(stage.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(stage.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { stage.delete() }
        changes.update { it + 1 }
    }

    companion object {
        private const val DEFAULT_SPARKLE = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 320"><path d="M160 20Q184 134 300 160Q184 186 160 300Q135 186 20 160Q135 135 160 20Z" fill="#ffd661" stroke="#803e25" stroke-width="12"/></svg>"""
        @Volatile private var instance: ExpressionLibrary? = null

        fun get(context: Context): ExpressionLibrary {
            val app = context.applicationContext
            return instance?.takeIf { it.app === app } ?: synchronized(this) {
                instance?.takeIf { it.app === app } ?: ExpressionLibrary(app).also { instance = it }
            }
        }

        fun read(source: File): ExpressionPack {
            require(source.isFile && source.length() in 1..ExpressionPack.MAX_PACK.toLong())
            return ExpressionPack.read(source.readText(Charsets.UTF_8))
        }
    }
}
