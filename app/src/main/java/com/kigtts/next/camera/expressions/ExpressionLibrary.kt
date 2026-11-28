package com.kigtts.next.camera.expressions

import android.content.Context
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.ManagementOrderStore
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
    private val illustrations = listOf("agni", "side_eye", "kim", "lau")
    private val favorites = listOf("giggle", "not_giggle", "wise")
    private val names = favorites + (ExpressionPresets.names - favorites.toSet()) + illustrations
    private val orderStore = ManagementOrderStore(app)

    fun choices(): List<ExpressionEffect> {
        val effects = catalog().associateBy { it.id }
        return orderStore.ordered(ORDER_KEY, effects.keys.toList()).map(effects::getValue)
    }

    @Synchronized fun reorder(order: List<String>) {
        orderStore.save(ORDER_KEY, order, catalog().map { it.id })
        changes.update { it + 1 }
    }

    private fun catalog(): List<ExpressionEffect> = names.mapNotNull { find("builtin.$it") } + directory.listFiles().orEmpty()
        .asSequence().filter { it.extension == "kigexpression" && !it.name.startsWith("builtin.") &&
            it.length() in 1..ExpressionPack.MAX_PACK.toLong() }
        .sortedBy { it.name }.take(128)
        .flatMap { runCatching { read(it).effects }.getOrDefault(emptyList()) }.toList()

    fun find(id: String): ExpressionEffect? {
        if (!id.matches(Regex("[a-z0-9._-]{1,96}"))) return null
        val preset = id.removePrefix("builtin.").takeIf { id.startsWith("builtin.") }
        if (preset != null && preset !in names) return null
        val stored = packFile(ownerOf(id)).takeIf(File::isFile)?.let { source ->
            runCatching { read(source).effects.firstOrNull { it.id == id } }.getOrNull()
        }
        return stored ?: preset?.let(::builtIn)
    }

    private fun builtIn(name: String): ExpressionEffect? {
        if (name !in illustrations) return ExpressionPresets.read(app, name)
        val svg = app.assets.open("camera-expressions/$name.svg").use {
            AppearanceAssets.readBounded(it, ExpressionPack.MAX_SVG).toString(Charsets.UTF_8)
        }
        return ExpressionEffect("builtin.$name", app.uiCopy("expression.ink.$name"),
            listOf(ExpressionLayer(svg, ExpressionAnchor.FACE, scale = .95f, x = .006f, y = .05f,
                hideUnderFringe = true)), blurFace = true)
    }

    fun newDraft() = ExpressionEffect("local.${UUID.randomUUID()}", "", listOf(
        ExpressionLayer(DEFAULT_SPARKLE, ExpressionAnchor.FOREHEAD, scale = .65f, y = -.18f)
    ))

    fun editable(effect: ExpressionEffect): ExpressionEffect = effect.withBitmapArtwork()

    @Synchronized fun save(effect: ExpressionEffect) {
        val packed = effect.withBitmapArtwork()
        CompiledExpression.compile(packed)
        val id = effect.id
        val owner = ownerOf(id)
        val replacement = when {
            id.matches(Regex("local\\.[a-f0-9-]{36}")) ||
                id.startsWith("builtin.") && id.removePrefix("builtin.") in names -> ExpressionPack(effects = listOf(packed))
            id.matches(Regex("pack\\.[a-f0-9-]{36}\\.[a-f0-9-]{36}")) -> {
                val installed = read(packFile(owner))
                require(installed.effects.any { it.id == id })
                installed.copy(effects = installed.effects.map { if (it.id == id) packed else it })
            }
            else -> error("Unknown sticker")
        }
        write(owner, replacement)
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
            effect.withBitmapArtwork().copy(id = "$namespace.${UUID.nameUUIDFromBytes(effect.id.toByteArray(Charsets.UTF_8))}")
        })
        write(namespace, installed)
        return installed.effects.first().id
    }

    fun share(effect: ExpressionEffect): android.content.Intent {
        val pack = ExpressionPack(effects = listOf(effect.withBitmapArtwork())).validate()
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
        val file = packFile(owner)
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

    private fun packFile(owner: String) = File(directory, "$owner.kigexpression")

    private fun ownerOf(id: String) = if (id.startsWith("pack.")) id.substringBeforeLast('.') else id

    companion object {
        private const val ORDER_KEY = "camera-face-stickers"
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
