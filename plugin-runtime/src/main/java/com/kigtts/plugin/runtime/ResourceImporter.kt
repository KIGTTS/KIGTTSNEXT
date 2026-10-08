package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.PluginStorage
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile

/** All source copies, staging, extracted resources and rollback files stay in one plugin's data root. */
object ResourceImporter {
    fun import(storage: PluginStorage, input: InputStream, directory: String, name: String, zip: Boolean): String {
        val target = storage.file(directory)
        require(directory.startsWith("models") || directory.startsWith("resources"))
        val scratch = File(storage.temporary, "import-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(scratch, "source")
        try {
            input.use { stream -> source.outputStream().use { out -> boundedCopy(stream, out) } }
            if (zip) {
                val unpacked = File(scratch, "unpacked").apply { mkdirs() }
                ZipFile(source).use { archive ->
                    val entries = archive.entries().toList()
                    require(entries.size in 1..10_000 && entries.map { it.name }.distinct().size == entries.size)
                    require(entries.all { PackageVerifier.safeName(it.name) && it.size in 0..MAX_RESOURCE_BYTES })
                    require(entries.sumOf { it.size } <= MAX_RESOURCE_BYTES)
                    entries.forEach { entry ->
                        val file = File(unpacked, entry.name)
                        if (entry.isDirectory) file.mkdirs() else {
                            file.parentFile!!.mkdirs()
                            archive.getInputStream(entry).use { stream -> file.outputStream().use { boundedCopy(stream, it) } }
                        }
                    }
                }
                val old = File(scratch, "old")
                target.parentFile!!.mkdirs()
                if (target.exists()) require(target.renameTo(old))
                try { require(unpacked.renameTo(target)) } catch (error: Exception) {
                    if (old.exists()) old.renameTo(target)
                    throw error
                }
                return directory
            }
            require(PackageVerifier.safeName(name) && !name.contains('/')) { "文件名不合法" }
            target.mkdirs()
            val destination = storage.file("$directory/$name")
            java.nio.file.Files.move(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return "$directory/$name"
        } finally { scratch.deleteRecursively() }
    }
    private fun boundedCopy(input: InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            val n = input.read(buffer); if (n < 0) return
            size += n; require(size <= MAX_RESOURCE_BYTES) { "资源过大" }
            output.write(buffer, 0, n)
        }
    }
}
