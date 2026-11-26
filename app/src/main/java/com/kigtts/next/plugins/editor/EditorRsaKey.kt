package com.kigtts.next.plugins.editor

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec

/** Reads the public components of an RSA PKCS#8 key independently of the Android provider. */
internal fun editorRsaPublicKey(factory: KeyFactory, encoded: ByteArray): ByteArray {
    val privateKeyInfo = ByteBuffer.wrap(encoded).readDer(0x30)
    privateKeyInfo.readDer(0x02) // PrivateKeyInfo version
    privateKeyInfo.readDer(0x30) // Algorithm identifier; validated by KeyFactory before calling this
    val pkcs1 = privateKeyInfo.readDer(0x04).readDer(0x30)
    pkcs1.readDer(0x02) // RSAPrivateKey version
    val modulus = pkcs1.readPositiveInteger(maxBytes = 513)
    val exponent = pkcs1.readPositiveInteger(maxBytes = 513)
    require(modulus.bitLength() in 2048..4096 && exponent > BigInteger.ONE && exponent < modulus && exponent.testBit(0))
    return factory.generatePublic(RSAPublicKeySpec(modulus, exponent)).encoded
}

private fun ByteBuffer.readPositiveInteger(maxBytes: Int): BigInteger {
    val entry = readDer(0x02)
    require(entry.remaining() in 1..maxBytes)
    return BigInteger(ByteArray(entry.remaining()).also { entry.get(it) }).also { require(it.signum() > 0) }
}

private fun ByteBuffer.readDer(tag: Int): ByteBuffer {
    require((get().toInt() and 255) == tag)
    val sizeByte = get().toInt() and 255
    val length = if (sizeByte < 128) sizeByte else {
        val count = sizeByte and 127
        require(count in 1..3 && count <= remaining())
        var size = 0
        repeat(count) { size = (size shl 8) or (get().toInt() and 255) }
        require(size >= 128)
        size
    }
    require(length <= remaining())
    val value = slice().apply { limit(length) }
    position(position() + length)
    return value
}
