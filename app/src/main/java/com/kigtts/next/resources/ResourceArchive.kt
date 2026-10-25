package com.kigtts.next.resources

import com.kigtts.plugin.api.*
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipFile

data class ResourceArchive(val directory: File, val manifest: ResourcePackageManifest, val content: ResourcePackageContent) {
    fun file(path: String): File = File(directory, path).also { require(safe(path) && it.isFile) { "资源文件不存在" } }
    val count: Int get() = when (manifest.resourceType) {
        ResourceType.FILTER -> content.filters.size
        ResourceType.FRAME -> content.frames.size
        ResourceType.STICKERS -> content.snapshot?.items?.size ?: 0
        ResourceType.PHOTOS -> content.snapshot?.cards?.size ?: 0
        else -> 1
    }
    companion object {
        fun safe(path: String) = path.length in 1..240 && !path.startsWith('/') && !path.contains('\\') && !path.contains(':') &&
            path.split('/').none { it.isBlank() || it in setOf(".", "..") } && path.none { it.code < 32 }
        fun hash(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun read(file: File, target: File, publisher: String): ResourceArchive {
            require(publisher.matches(Regex("[a-f0-9]{64}")) && file.length() in 1..256L * 1024 * 1024)
            target.mkdirs()
            try { return ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(entries.size in 4..4096 && entries.map { it.name.lowercase() }.distinct().size == entries.size)
                require(entries.all { !it.isDirectory && safe(it.name) && it.size in 0..512L*1024*1024 })
                require(entries.sumOf { it.size } <= 768L*1024*1024) { "资源包解压后过大" }
                fun bytes(name: String, limit: Int): ByteArray {
                    val entry = requireNotNull(zip.getEntry(name)) { "资源包缺少必要文件" }
                    require(entry.size in 1..limit.toLong())
                    return zip.getInputStream(entry).use { com.kigtts.next.theme.AppearanceAssets.readBounded(it, limit) }
                }
                val raw = bytes("resource.json", 256*1024)
                val manifest = PluginJson.decodeFromString<ResourcePackageManifest>(raw.toString(Charsets.UTF_8))
                require(manifest.schemaVersion == 1 && manifest.apiVersion <= PLUGIN_API_VERSION && manifest.apiVersion >= 29 && manifest.minSdk <= android.os.Build.VERSION.SDK_INT)
                require(manifest.id.matches(Regex("[a-z][a-z0-9.-]{0,63}")) && manifest.name.length in 1..128 && manifest.versionCode > 0)
                val keyBytes = bytes("signature/public.der", 8192)
                require(MessageDigest.getInstance("SHA-256").digest(keyBytes).joinToString("") { "%02x".format(it) } == publisher) { "资源包发布者不匹配" }
                val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
                require((key as java.security.interfaces.RSAPublicKey).modulus.bitLength() >= 2048)
                require(Signature.getInstance("SHA256withRSA").run { initVerify(key); update(raw); verify(bytes("signature/manifest.sig", 8192)) }) { "资源包签名无效" }
                val payload = entries.filter { it.name !in setOf("resource.json", "signature/public.der", "signature/manifest.sig") }
                require(manifest.files.keys == payload.map { it.name }.toSet() && manifest.files.values.all { it.matches(Regex("[a-f0-9]{64}")) })
                payload.forEach { entry ->
                    val output = File(target, entry.name); output.parentFile!!.mkdirs()
                    zip.getInputStream(entry).use { input -> output.outputStream().use { out ->
                        val buffer = ByteArray(65536); var total = 0L
                        while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= entry.size); out.write(buffer,0,n) }
                        require(total == entry.size)
                    } }
                    require(hash(output) == manifest.files[entry.name]) { "资源包文件校验失败" }
                }
                require(safe(manifest.content))
                val descriptor = File(target, manifest.content)
                require(descriptor.length() in 1..4L*1024*1024)
                val content = PluginJson.decodeFromString<ResourcePackageContent>(descriptor.readText(Charsets.UTF_8))
                ResourceArchive(target, manifest, content).also { it.validate() }
            } } catch (e: Exception) { target.deleteRecursively(); throw e }
        }
    }
}

private fun ResourceArchive.validate() {
    require(content.schemaVersion == 1 && content.filters.size <= 32 && content.imageFiles.size <= 2048 && content.frames.size <= 128)
    content.payload?.let(::file); content.filters.forEach { file(it.path) }; content.imageFiles.values.forEach(::file)
    when (manifest.resourceType) {
        ResourceType.QUICK_TEXT, ResourceType.VOICE -> require(content.payload != null && content.snapshot == null && content.frames.isEmpty() && content.filters.isEmpty())
        ResourceType.FILTER -> require(content.filters.isNotEmpty() && content.payload == null && content.snapshot == null && content.frames.isEmpty())
        ResourceType.FRAME -> require(content.frames.isNotEmpty() && content.payload == null && content.filters.isEmpty() && content.snapshot == null)
        ResourceType.PHOTOS -> require(content.snapshot?.cards?.isNotEmpty() == true && content.payload == null && content.filters.isEmpty())
        ResourceType.STICKERS -> require(content.snapshot?.items?.isNotEmpty() == true && content.snapshot?.cards?.isEmpty() == true && content.payload == null && content.filters.isEmpty() && content.frames.isEmpty())
    }
}
