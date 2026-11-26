package com.kigtts.next.plugins.editor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.core.content.edit
import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.runtime.PluginManager
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable internal data class EditorSigningKey(val fingerprint: String, val name: String)
@Serializable private data class StoredEditorKey(val info: EditorSigningKey, val iv: String, val ciphertext: String)
internal data class EditorSignature(val publicKey: ByteArray, val signature: ByteArray)

internal class EditorSigningVault(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "plugin-signing")
    private val preferences = context.getSharedPreferences("plugin-signing-selection", Context.MODE_PRIVATE)
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private val rsa = KeyFactory.getInstance("RSA")
    private val alias = "kigtts-editor-signatures-v1"

    @Synchronized fun list(): List<EditorSigningKey> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" }.mapNotNull { runCatching { PluginJson.decodeFromString<StoredEditorKey>(it.readText()).info }.getOrNull() }
        .sortedBy { it.name.lowercase() }

    @Synchronized fun generate(name: String): EditorSigningKey {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val raw = pair.private.encoded
        return try { save(name, raw) } finally { raw.fill(0) }
    }

    @Synchronized fun import(name: String, bytes: ByteArray, password: CharArray): EditorSigningKey {
        require(bytes.size in 1..65_536)
        val privateBytes = when {
            bytes.first() == '{'.code.toByte() -> EditorSigningTransfer.open(bytes, password)
            else -> runCatching { key(bytes); bytes }.getOrElse {
                val store = KeyStore.getInstance("PKCS12")
                bytes.inputStream().use { store.load(it, password) }
                val aliases = store.aliases().toList().filter(store::isKeyEntry)
                require(aliases.size == 1) { "Select a PKCS#12 file with one private key" }
                requireNotNull(store.getKey(aliases.single(), password).encoded)
            }
        }
        return try { save(name, privateBytes) } finally { if (privateBytes !== bytes) privateBytes.fill(0) }
    }

    @Synchronized fun export(fingerprint: String, password: CharArray): ByteArray {
        val raw = read(fingerprint)
        return try { EditorSigningTransfer.seal(raw, password) } finally { raw.fill(0) }
    }

    /** Existing desktop/editor packages keep the same identity after upgrading. */
    @Synchronized fun migrateLegacy(manager: PluginManager) {
        if (preferences.getBoolean("legacy-migrated", false)) return
        val lua = File(manager.paths.data, "runtime.lua/config/lua-publisher.json")
        val luaMigrated = lua.isFile && runCatching {
            val raw = PluginJson.parseToJsonElement(lua.readText()).jsonObject
            val key = decoder.decode(raw.getValue("private").jsonPrimitive.content)
            try { save("Lua", key) } finally { key.fill(0) }
        }.isSuccess
        val native = File(manager.paths.data, "tools.native-builder/config/publisher-private.der")
        val nativeMigrated = native.isFile && runCatching {
            val key = native.readBytes()
            try { save("Java / Kotlin", key) } finally { key.fill(0) }
        }.isSuccess
        // A previously unused editor may create its default key later. Recheck until both are present.
        if (luaMigrated && nativeMigrated) preferences.edit { putBoolean("legacy-migrated", true) }
    }

    fun selected(owner: String, project: String): String? = preferences.getString("key/$owner/$project", null)
    fun select(owner: String, project: String, fingerprint: String?) {
        fingerprint?.let { require(file(it).isFile) }
        preferences.edit { putString("key/$owner/$project", fingerprint) }
    }

    @Synchronized fun sign(fingerprint: String, manifest: ByteArray): EditorSignature {
        val raw = read(fingerprint)
        return try {
            val private = key(raw)
            val signature = Signature.getInstance("SHA256withRSA").apply { initSign(private); update(manifest) }.sign()
            EditorSignature(publicKey(private), signature)
        } finally { raw.fill(0) }
    }

    private fun save(name: String, bytes: ByteArray): EditorSigningKey {
        require(name.trim().length in 1..64)
        val private = key(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKey(private)).joinToString("") { "%02x".format(it) }
        val target = file(hash)
        if (target.isFile) return PluginJson.decodeFromString<StoredEditorKey>(target.readText()).info
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        cipher.updateAAD(hash.toByteArray(Charsets.UTF_8))
        val info = EditorSigningKey(hash, name.trim())
        val stored = StoredEditorKey(info, encoder.encodeToString(cipher.iv), encoder.encodeToString(cipher.doFinal(bytes)))
        check(directory.mkdirs() || directory.isDirectory)
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            output.write(PluginJson.encodeToString(stored).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Exception) { atomic.failWrite(output); throw error }
        return info
    }

    private fun read(fingerprint: String): ByteArray {
        val stored = PluginJson.decodeFromString<StoredEditorKey>(AtomicFile(file(fingerprint)).readFully().toString(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, decoder.decode(stored.iv)))
            updateAAD(fingerprint.toByteArray(Charsets.UTF_8))
        }
        return cipher.doFinal(decoder.decode(stored.ciphertext))
    }

    private fun file(hash: String): File {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        return File(directory, "$hash.json")
    }

    private fun key(bytes: ByteArray) = (rsa.generatePrivate(PKCS8EncodedKeySpec(bytes)) as RSAPrivateCrtKey).also {
        require(it.modulus.bitLength() in 2048..4096)
    }

    private fun publicKey(key: RSAPrivateCrtKey): ByteArray =
        rsa.generatePublic(RSAPublicKeySpec(key.modulus, key.publicExponent)).encoded

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
}
