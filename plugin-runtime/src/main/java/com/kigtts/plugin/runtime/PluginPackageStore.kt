package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.PluginJson
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile

/** Atomic pointer switch leaves the previous version usable if verification or extraction fails. */
class PluginPackageStore(val paths: PluginPaths) {
    private val verifier = PackageVerifier(paths)
    fun inspect(file: File) = verifier.inspect(file)
    @Synchronized fun installed(): List<InstalledPlugin> = paths.packages.listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith('.') }.mapNotNull { installed(it.name) }
        .sortedBy { it.manifest.name }

    fun installed(id: String): InstalledPlugin? = runCatching {
        val directory = paths.packageDirectory(id)
        val current = currentDirectory(id)
        val manifest = PluginJson.decodeFromString<PackageManifest>(File(current, MANIFEST).readText())
        InstalledPlugin(manifest, File(directory, "publisher").readText().trim(), !File(directory, "disabled").exists())
    }.getOrNull()

    fun currentDirectory(id: String): File {
        val root = paths.packageDirectory(id)
        val version = File(root, "current").readText().trim()
        require(version.matches(Regex("v[0-9]+-[a-f0-9-]+")))
        return File(root, version).also { require(it.isDirectory) }
    }

    @Synchronized fun install(file: File, trustedPublisher: String, allowVersionChange: Boolean = false): InstalledPlugin {
        val preview = inspect(file)
        require(preview.publisherSha256 == trustedPublisher) { "发布者与确认内容不符" }
        val previous = installed().firstOrNull { it.manifest.id == preview.manifest.id }
        if (previous != null) {
            require(previous.publisherSha256 == preview.publisherSha256) { "更新包发布者签名不匹配" }
            require(preview.manifest.versionCode > previous.manifest.versionCode ||
                allowVersionChange && preview.manifest.versionCode != previous.manifest.versionCode) { "请使用版本号更高的更新包，或明确确认切换版本" }
        }
        val root = paths.packageDirectory(preview.manifest.id).apply { mkdirs() }
        val version = "v${preview.manifest.versionCode}-${UUID.randomUUID()}"
        val destination = File(root, version).apply { mkdirs() }
        try {
            ZipFile(file).use { zip -> zip.entries().toList().forEach { entry ->
                val target = File(destination, entry.name)
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile!!.mkdirs()
                    FileOutputStream(target).use { output ->
                        // Android 14+: mark executable payload immutable before writing through the open fd.
                        if (entry.name.startsWith("code/") || entry.name.startsWith("lib/")) require(target.setReadOnly())
                        zip.getInputStream(entry).use { it.copyTo(output) }
                        output.fd.sync()
                    }
                }
            } }
            val pointer = File(root, "current.new")
            File(root, "publisher").writeText(preview.publisherSha256)
            FileOutputStream(pointer).use { it.write(version.toByteArray()); it.fd.sync() }
            java.nio.file.Files.move(pointer.toPath(), File(root, "current").toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            if (previous?.enabled != false) File(root, "disabled").delete()
            root.listFiles().orEmpty().filter { it.isDirectory && it != destination }.forEach { it.deleteRecursively() }
        } catch (error: Exception) { destination.deleteRecursively(); throw error }
        return InstalledPlugin(preview.manifest, preview.publisherSha256, previous?.enabled != false)
    }
    @Synchronized fun enabled(id: String, enabled: Boolean) {
        val marker = File(paths.packageDirectory(id), "disabled")
        require(paths.packageDirectory(id).isDirectory)
        if (enabled) marker.delete() else marker.writeText("disabled")
    }
    /** Call only after sessions have ended and the worker process has stopped. */
    @Synchronized fun uninstall(id: String) {
        val packageDir = paths.packageDirectory(id)
        val dataDir = paths.storage(id).root
        require(packageDir.deleteRecursively() && dataDir.deleteRecursively()) { "插件文件未能完整清理" }
    }
    fun verifyInstalled(id: String): PackageManifest {
        val directory = currentDirectory(id)
        clearRuntimeCodeCache(directory)
        val raw = File(directory, MANIFEST).readBytes()
        val manifest = PluginJson.decodeFromString<PackageManifest>(raw.toString(Charsets.UTF_8))
        require(manifest.id == id && manifest.apiVersion in com.kigtts.plugin.api.MIN_PLUGIN_API_VERSION..com.kigtts.plugin.api.PLUGIN_API_VERSION)
        val key = java.security.KeyFactory.getInstance("RSA").generatePublic(
            java.security.spec.X509EncodedKeySpec(File(directory, PUBLIC_KEY).readBytes()))
        require(File(directory, PUBLIC_KEY).readBytes().sha256() == File(paths.packageDirectory(id), "publisher").readText().trim()) { "已安装插件发布者发生变化" }
        require(java.security.Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update(raw); verify(File(directory, SIGNATURE).readBytes())
        }) { "已安装插件的签名损坏" }
        val payload = directory.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(directory).invariantSeparatorsPath }.filter { it !in setOf(MANIFEST, PUBLIC_KEY, SIGNATURE) }.toSet()
        require(payload == manifest.files.keys) { "已安装插件出现清单之外的文件" }
        manifest.files.forEach { (path, hash) ->
            require(PackageVerifier.safeName(path) && File(directory, path).sha256() == hash) { "已安装插件文件损坏" }
        }
        return manifest
    }
    /** ART creates oat/profile files beside dynamically loaded DEX. They are disposable, unsigned
     * runtime output, never package payload. Delete before verification/load instead of trusting them. */
    private fun clearRuntimeCodeCache(directory: File) {
        val cache = File(directory, "code/oat").toPath()
        require(cache.normalize().startsWith(directory.toPath().normalize()))
        if (!java.nio.file.Files.exists(cache, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        java.nio.file.Files.walkFileTree(cache, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path, attributes: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                java.nio.file.Files.delete(file)
                return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: java.nio.file.Path, error: java.io.IOException?): java.nio.file.FileVisitResult {
                if (error != null) throw error
                java.nio.file.Files.delete(dir)
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
    }
}
