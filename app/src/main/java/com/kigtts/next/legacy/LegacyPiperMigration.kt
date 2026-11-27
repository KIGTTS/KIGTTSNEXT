package com.kigtts.next.legacy

import com.kigtts.next.audio.LegacyPiperPacks
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Seed the Piper data directory before the engine is installed so pending packs survive restarts. */
internal fun migrateLegacyPiperPacks(content: LegacyContent, pluginData: File): Boolean {
    val source = content.resourceRoot?.let { File(it, "voice") } ?: return false
    if (Files.isSymbolicLink(source.toPath())) return false
    val destination = File(pluginData, "engine.piper/models/voices")
    var found = false
    source.listFiles().orEmpty().asSequence()
        .filter { it.isDirectory && !it.name.startsWith(".") && !Files.isSymbolicLink(it.toPath()) }
        .take(1024).filter(LegacyPiperPacks::valid).forEach { pack ->
            val manifest = File(pack, "manifest.json")
            val identity = UUID.nameUUIDFromBytes((pack.name + "\n" + manifest.readText(Charsets.UTF_8)).toByteArray(Charsets.UTF_8))
            val target = File(destination, "voice-$identity")
            if (!target.exists()) {
                val copied = pack.walkTopDown().filter { it.isFile || Files.isSymbolicLink(it.toPath()) }.take(4097).toList()
                require(copied.size <= 4096 && copied.sumOf(File::length) <= 2L * 1024 * 1024 * 1024)
                require(copied.none { Files.isSymbolicLink(it.toPath()) })
                val staging = File(destination, ".legacy-${UUID.randomUUID()}").apply { mkdirs() }
                try {
                    copied.forEach { file -> file.copyTo(File(staging, file.relativeTo(pack).path)) }
                    try {
                        Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                        Files.move(staging.toPath(), target.toPath())
                    }
                } finally { staging.deleteRecursively() }
            }
            found = true
        }
    return found
}
