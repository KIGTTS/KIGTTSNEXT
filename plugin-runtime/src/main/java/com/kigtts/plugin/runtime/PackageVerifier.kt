package com.kigtts.plugin.runtime

import android.os.Build
import com.kigtts.plugin.api.PLUGIN_API_VERSION
import com.kigtts.plugin.api.PluginJson
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipFile

internal const val MANIFEST = "plugin.json"
internal const val PUBLIC_KEY = "signature/public.der"
internal const val SIGNATURE = "signature/manifest.sig"
internal fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
internal fun File.sha256(): String = inputStream().use { stream ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    digest.digest().joinToString("") { "%02x".format(it) }
}

/** Signature authenticates every payload hash. Trust is explicitly confirmed on first install. */
class PackageVerifier(private val paths: PluginPaths) {
    fun inspect(file: File): PackagePreview = ZipFile(file).use { zip ->
        val entries = zip.entries().toList()
        require(entries.size in 4..4096) { "插件包文件数量不合法" }
        val names = entries.map { it.name }
        require(names.distinct().size == names.size) { "插件包包含重复路径" }
        require(entries.all { safeName(it.name) && it.size in 0..512L * 1024 * 1024 }) { "插件包包含不合法的路径或过大文件" }
        require(entries.sumOf { it.size } <= 768L * 1024 * 1024) { "插件包过大，请把模型作为独立资源导入" }
        fun read(name: String, limit: Int): ByteArray {
            val entry = zip.getEntry(name) ?: error("缺少 $name")
            require(entry.size <= limit)
            return zip.getInputStream(entry).use { it.readBytes() }
        }
        val raw = read(MANIFEST, 256 * 1024)
        val manifest = PluginJson.decodeFromString<PackageManifest>(raw.toString(Charsets.UTF_8))
        paths.validateId(manifest.id)
        require(manifest.apiVersion == PLUGIN_API_VERSION) { "插件接口版本不兼容" }
        require(manifest.runtime == "android-dex" && manifest.minSdk <= Build.VERSION.SDK_INT) { "插件运行时不兼容" }
        require(manifest.versionCode > 0 && manifest.version.isNotBlank() && manifest.name.isNotBlank())
        require(manifest.entryClass.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")))
        require(manifest.abis.isEmpty() || manifest.abis.any { it in Build.SUPPORTED_ABIS }) { "插件不支持此设备架构" }
        val payload = entries.filter { !it.isDirectory && it.name !in setOf(MANIFEST, PUBLIC_KEY, SIGNATURE) }
        require(manifest.files.keys == payload.map { it.name }.toSet()) { "文件清单与插件内容不匹配" }
        require("code/classes.dex" in manifest.files) { "缺少 Android DEX 入口" }
        val keyBytes = read(PUBLIC_KEY, 8192)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        require((key as java.security.interfaces.RSAPublicKey).modulus.bitLength() >= 2048)
        require(Signature.getInstance("SHA256withRSA").run { initVerify(key); update(raw); verify(read(SIGNATURE, 8192)) }) { "插件签名不正确" }
        payload.forEach { entry ->
            val digest = MessageDigest.getInstance("SHA-256")
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual == manifest.files[entry.name]) { "插件文件校验失败：${entry.name}" }
        }
        PackagePreview(manifest, keyBytes.sha256())
    }

    companion object {
        fun safeName(name: String): Boolean = name.isNotEmpty() && name.length <= 240 && !name.contains('\\') &&
            !name.contains(':') && !name.contains('\u0000') && !name.startsWith('/') &&
            name.removeSuffix("/").split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
}
