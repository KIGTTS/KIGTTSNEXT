package com.kigtts.next

import com.kigtts.next.media.MotionPhoto
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MotionPhotoTest {
    private val directory = File(System.getProperty("java.io.tmpdir"), "motion-test-" + java.util.UUID.randomUUID()).apply { mkdirs() }
    @After fun cleanup() { directory.deleteRecursively() }
    private val video = ByteArrayOutputStream().apply { DataOutputStream(this).apply { writeInt(16); writeBytes("ftyp"); writeBytes("mp42"); writeInt(0) } }.toByteArray()
    private fun fixture(attributes: String, item: String = "", tail: ByteArray = video): File {
        val xml = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:GCamera="http://ns.google.com/photos/1.0/camera/" xmlns:Item="http://ns.google.com/photos/1.0/container/item/" $attributes>$item</rdf:Description></rdf:RDF></x:xmpmeta>"""
        return File(directory, java.util.UUID.randomUUID().toString() + ".jpg").apply { writeBytes(byteArrayOf(-1,-40) + xml.toByteArray() + byteArrayOf(-1,-39) + tail) }
    }
    @Test fun modernDirectoryWinsOverDeprecatedOffsetAndExtractsExactClip() {
        val file = fixture("GCamera:MotionPhoto=\"1\" GCamera:MicroVideoOffset=\"9999\"", "<rdf:li Item:Semantic=\"MotionPhoto\" Item:Length=\"16\"/>")
        assertEquals(MotionPhoto.Clip(file.length() - 16, 16), MotionPhoto.locate(file))
        val clip = File(directory, "clip.mp4"); assertTrue(MotionPhoto.extract(file, clip)); assertArrayEquals(video, clip.readBytes())
    }
    @Test fun legacyAndElementEncodedCameraMetadataAreSupported() {
        assertNotNull(MotionPhoto.locate(fixture("GCamera:MicroVideoOffset=\"16\"")))
        assertNotNull(MotionPhoto.locate(fixture("", "<GCamera:MotionPhoto>1</GCamera:MotionPhoto><GCamera:MicroVideoOffset>16</GCamera:MicroVideoOffset>")))
    }
    @Test fun disabledFlagsTruncatedFilesNegativeLengthsAndWrongNamespacesStayStill() {
        for (attributes in listOf("GCamera:MotionPhoto=\"0\" GCamera:MicroVideoOffset=\"16\"", "GCamera:MicroVideoOffset=\"-16\"", "GCamera:MicroVideoOffset=\"999999\"", "Item:MicroVideoOffset=\"16\"")) assertNull(MotionPhoto.locate(fixture(attributes)))
        assertNull(MotionPhoto.locate(fixture("GCamera:MicroVideoOffset=\"16\"", tail = ByteArray(16))))
        assertNull(MotionPhoto.locate(fixture("GCamera:MicroVideoOffset=\"16\"", tail = video.copyOf(8))))
    }
    @Test fun externalEntitiesAndMalformedXmlAreIgnored() {
        val malicious = File(directory, "unsafe.jpg").apply { writeText("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><!DOCTYPE a [<!ENTITY ext SYSTEM 'file:///etc/passwd'>]><a>&ext;</a></x:xmpmeta>") }
        assertNull(MotionPhoto.locate(malicious))
        assertNull(MotionPhoto.locate(fixture("GCamera:MicroVideoOffset=bad")))
    }
}
