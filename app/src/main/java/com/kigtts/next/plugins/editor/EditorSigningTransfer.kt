package com.kigtts.next.plugins.editor

import com.kigtts.plugin.api.PluginJson
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable

@Serializable private data class KeyArchive(val format: String, val salt: String, val iv: String, val ciphertext: String)

internal object EditorSigningTransfer {
    private const val FORMAT = "kigtts-publisher-key-v1"
    private val random = SecureRandom()

    fun seal(privateKey: ByteArray, password: CharArray): ByteArray {
        require(password.size in 8..128)
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val encrypted = cipher(Cipher.ENCRYPT_MODE, password, salt, iv).doFinal(privateKey)
        val base64 = Base64.getEncoder()
        return PluginJson.encodeToString(KeyArchive(FORMAT, base64.encodeToString(salt), base64.encodeToString(iv),
            base64.encodeToString(encrypted))).toByteArray(Charsets.UTF_8)
    }

    fun open(bytes: ByteArray, password: CharArray): ByteArray {
        require(password.size in 8..128)
        val archive = PluginJson.decodeFromString<KeyArchive>(bytes.toString(Charsets.UTF_8))
        require(archive.format == FORMAT)
        val base64 = Base64.getDecoder()
        return cipher(Cipher.DECRYPT_MODE, password, base64.decode(archive.salt), base64.decode(archive.iv))
            .doFinal(base64.decode(archive.ciphertext))
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        require(salt.size == 16 && iv.size == 12)
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        val raw = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, iv))
                updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
            }
        } finally { raw.fill(0) }
    }
}
