package com.kigtts.next

import com.kigtts.next.plugins.editor.EditorSigningTransfer
import com.kigtts.next.plugins.editor.validEditorPackageName
import javax.crypto.AEADBadTagException
import org.junit.Assert.*
import org.junit.Test

class EditorSigningTransferTest {
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
