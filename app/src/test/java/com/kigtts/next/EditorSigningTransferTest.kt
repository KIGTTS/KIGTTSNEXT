package com.kigtts.next

import com.kigtts.next.plugins.editor.EditorSigningTransfer
import com.kigtts.next.plugins.editor.editorRsaPublicKey
import com.kigtts.next.plugins.editor.validEditorPackageName
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.AEADBadTagException
import org.junit.Assert.*
import org.junit.Test

class EditorSigningTransferTest {
    @Test fun derivesThePublishedRsaIdentityFromPkcs8() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val factory = KeyFactory.getInstance("RSA")
        val encoded = pair.private.encoded
        try {
            val public = editorRsaPublicKey(factory, encoded)
            assertArrayEquals(pair.public.encoded, public)
            val manifest = "plugin.json".toByteArray()
            val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(manifest) }.sign()
            assertTrue(Signature.getInstance("SHA256withRSA").run {
                initVerify(factory.generatePublic(X509EncodedKeySpec(public))); update(manifest); verify(signature)
            })
            assertThrows(IllegalArgumentException::class.java) { editorRsaPublicKey(factory, encoded.copyOf(80)) }
        } finally { encoded.fill(0) }
    }

    @Test fun backupRequiresCorrectPasswordAndUsesFreshEncryption() {
        val raw = byteArrayOf(1, 2, 18, -120)
        val password = "example-backup-password".toCharArray()
        val first = EditorSigningTransfer.seal(raw, password)
        val second = EditorSigningTransfer.seal(raw, password)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(raw, EditorSigningTransfer.open(first, password))
        assertThrows(AEADBadTagException::class.java) {
            EditorSigningTransfer.open(first, "wrong-test-password".toCharArray())
        }
    }

    @Test fun packageNamesUseReverseDomainAndReserveHostNamespaces() {
        assertTrue(validEditorPackageName("com.example.project"))
        assertFalse(validEditorPackageName("unsigned"))
        assertFalse(validEditorPackageName("com.example.some_app"))
        assertFalse(validEditorPackageName("tools.nativebuilder"))
    }
}
