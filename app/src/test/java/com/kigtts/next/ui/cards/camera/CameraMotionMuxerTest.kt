package com.kigtts.next.ui.cards.camera

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.nio.ByteBuffer

/** Platform packet fixtures verify trim/clock/resource semantics, independently of a hardware codec. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], shadows = [CameraMotionMuxerTest.Extractor::class, CameraMotionMuxerTest.Muxer::class])
class CameraMotionMuxerTest {
    @get:Rule val temporary = TemporaryFolder()
    @Before fun setup() { Extractor.sources.clear(); Extractor.released = 0; Muxer.samples.clear(); Muxer.released = 0 }

    private fun format(width: Int = 320, configuration: Byte = 1) = MediaFormat.createVideoFormat("video/avc", width, 240).apply {
        setInteger(MediaFormat.KEY_FRAME_RATE, 10)
        setInteger(MediaFormat.KEY_ROTATION, 90)
        setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(configuration, 2, 3)))
    }
    private fun part(name: String, start: Long, format: MediaFormat = format(), encrypted: Boolean = false): CameraMotionPart {
        val file = temporary.newFile(name).apply { writeBytes(ByteArray(32)) }
        Extractor.sources[file.absolutePath] = Source(format, (0..39).map { index ->
            Sample(index * 100_000L, (if (index % 10 == 0) MediaExtractor.SAMPLE_FLAG_SYNC else 0) or
                if (encrypted) MediaExtractor.SAMPLE_FLAG_ENCRYPTED else 0, byteArrayOf(index.toByte()))
        })
        return CameraMotionPart(file, start, 4000)
    }

    @Test fun preShutterSyncFrameAndRealSegmentGapArePreservedWithBoundedPostRoll() {
        val first = part("before.mp4", 10_000); val second = part("after.mp4", 14_050)
        val output = File(temporary.root, "clip.mp4")
        val result = CameraMotionMuxer.write(listOf(second, first), 15_000, output)
        assertTrue(output.isFile); assertFalse(File(output.path + ".pending").exists())
        assertEquals(90, result.rotationDegrees); assertEquals(2000, result.shutterPositionMs)
        assertEquals(3250, result.durationMs)
        assertEquals(MediaCodec.BUFFER_FLAG_KEY_FRAME, Muxer.samples.first().flags)
        assertEquals(0, Muxer.samples.first().time)
        assertEquals(900_000, Muxer.samples[9].time)
        assertEquals(1_050_000, Muxer.samples[10].time) // second segment starts 50 ms after the real boundary
        assertTrue(Muxer.samples.last().time <= 3_200_000)
        assertEquals(2, Extractor.released); assertEquals(1, Muxer.released)
    }

    @Test fun changedDecoderConfigurationRollsBackTheIncompleteOutputAndReleasesBothReaders() {
        val before = part("before.mp4", 10_000)
        val after = part("after.mp4", 14_050, format(configuration = 9))
        val destination = File(temporary.root, "clip.mp4")
        assertThrows(IllegalArgumentException::class.java) { CameraMotionMuxer.write(listOf(before, after), 15_000, destination) }
        assertFalse(destination.exists()); assertFalse(File(destination.path + ".pending").exists())
        assertEquals(2, Extractor.released); assertEquals(1, Muxer.released)
        assertTrue(before.file.isFile); assertTrue(after.file.isFile)
    }

    @Test fun encryptedSamplesAreRejectedAndCannotProduceAMislabelledLivePhotograph() {
        val part = part("encrypted.mp4", 10_000, encrypted = true)
        val destination = File(temporary.root, "clip.mp4")
        assertThrows(IllegalArgumentException::class.java) { CameraMotionMuxer.write(listOf(part), 12_000, destination) }
        assertFalse(destination.exists()); assertFalse(File(destination.path + ".pending").exists())
        assertEquals(1, Extractor.released); assertEquals(1, Muxer.released)
    }

    @Test fun missingShutterCoverageIsRejectedAndCsdComparisonDoesNotMoveTheBuffers() {
        val part = part("late.mp4", 10_000)
        assertThrows(IllegalArgumentException::class.java) { cameraMotionWindow(9000, listOf(part)) }
        val a = format(); val b = format(); a.getByteBuffer("csd-0")!!.position(2)
        assertTrue(cameraMotionFormatsMatch(a, b)); assertEquals(2, a.getByteBuffer("csd-0")!!.position())
        assertFalse(cameraMotionFormatsMatch(a, format(width = 640)))
    }

    data class Sample(val time: Long, val flags: Int, val bytes: ByteArray)
    data class Source(val format: MediaFormat, val samples: List<Sample>)

    @Implements(MediaExtractor::class)
    class Extractor {
        private lateinit var source: Source
        private var selected = false; private var position = 0
        @Implementation fun __constructor__() {}
        @Implementation fun setDataSource(path: String) { source = sources.getValue(path) }
        @Implementation fun getTrackCount() = 1
        @Implementation fun getTrackFormat(index: Int) = source.format
        @Implementation fun selectTrack(index: Int) { selected = true }
        @Implementation fun unselectTrack(index: Int) { selected = false }
        @Implementation fun seekTo(time: Long, mode: Int) {
            position = source.samples.indexOfLast { it.time <= time && it.flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0 }.coerceAtLeast(0)
        }
        @Implementation fun getSampleTime() = source.samples.getOrNull(position)?.time ?: -1L
        @Implementation fun getSampleFlags() = source.samples.getOrNull(position)?.flags ?: 0
        @Implementation fun getSampleTrackIndex() = if (selected && position < source.samples.size) 0 else -1
        @Implementation fun getSampleSize() = source.samples.getOrNull(position)?.bytes?.size?.toLong() ?: -1
        @Implementation fun readSampleData(buffer: ByteBuffer, offset: Int): Int {
            val bytes = source.samples[position].bytes; buffer.position(offset); buffer.put(bytes); return bytes.size
        }
        @Implementation fun advance(): Boolean { position++; return position < source.samples.size }
        @Implementation fun release() { released++ }
        companion object { val sources = mutableMapOf<String, Source>(); var released = 0 }
    }

    @Implements(MediaMuxer::class)
    class Muxer {
        private lateinit var output: File
        @Implementation fun __constructor__(path: String, format: Int) { output = File(path) }
        @Implementation fun setOrientationHint(degrees: Int) {}
        @Implementation fun addTrack(format: MediaFormat) = 0
        @Implementation fun start() { output.writeBytes(byteArrayOf(0, 0, 0, 16)) }
        @Implementation fun writeSampleData(index: Int, data: ByteBuffer, info: MediaCodec.BufferInfo) {
            val bytes = ByteArray(info.size); data.duplicate().apply { position(info.offset) }.get(bytes)
            samples += Sample(info.presentationTimeUs, info.flags, bytes); output.appendBytes(bytes)
        }
        @Implementation fun stop() {}
        @Implementation fun release() { released++ }
        companion object { val samples = mutableListOf<Sample>(); var released = 0 }
    }
}
