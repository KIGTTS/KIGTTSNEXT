package com.kigtts.next.ui.cards.camera

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Bounded packet remuxing: retain the preceding keyframe and the real pre/post-shutter timestamps. */
internal object CameraMotionMuxer {
    private data class Track(val input: Int, val kind: String, val format: MediaFormat)

    fun write(parts: List<CameraMotionPart>, shutterAtMs: Long, destination: File): CameraMotionVideo {
        val window = cameraMotionWindow(shutterAtMs, parts)
        val selected = parts.sortedBy { it.startedAtMs }.filter(window::intersects)
        require(selected.isNotEmpty() && selected.all { it.file.isFile && it.file.length() in 1..100L * 1024 * 1024 }) {
            "无法读取实况片段"
        }
        require(!destination.exists()) { "实况输出已存在" }
        val directory = requireNotNull(destination.parentFile) { "实况输出目录无效" }
        check(directory.isDirectory || directory.mkdirs())
        val stage = File(directory, destination.name + ".pending")
        var muxer: MediaMuxer? = null
        var started = false
        var originUs = -1L
        var finalUs = -1L
        var frameStepUs = 33_333L
        var rotation = 0
        var reference: List<Track>? = null
        val outputTracks = mutableMapOf<String, Int>()
        val priorEnds = mutableMapOf<String, Long>()
        try {
            selected.forEach { part ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(part.file.absolutePath)
                    val tracks = tracks(extractor)
                    val video = tracks.first { it.kind == "video" }
                    val orientation = if (video.format.containsKey(MediaFormat.KEY_ROTATION)) video.format.getInteger(MediaFormat.KEY_ROTATION) else 0
                    require(orientation in setOf(0, 90, 180, 270)) { "实况方向无效" }
                    if (reference == null) {
                        rotation = orientation; reference = tracks
                        extractor.selectTrack(video.input)
                        extractor.seekTo(window.localStartUs(part), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        require(extractor.sampleTime >= 0 && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            "实况片段缺少可播放的关键帧"
                        }
                        originUs = window.absoluteUs(part, extractor.sampleTime)
                        extractor.unselectTrack(video.input)
                        val frameRate = if (video.format.containsKey(MediaFormat.KEY_FRAME_RATE)) video.format.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
                        frameStepUs = 1_000_000L / frameRate.coerceIn(1, 120)
                        muxer = MediaMuxer(stage.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { output ->
                            output.setOrientationHint(rotation)
                            tracks.forEach { outputTracks[it.kind] = output.addTrack(it.format) }
                            output.start(); started = true
                        }
                    } else {
                        require(orientation == rotation && formatsMatch(requireNotNull(reference), tracks)) {
                            "实况片段格式已变化，请重新拍摄"
                        }
                    }
                    tracks.forEach { extractor.selectTrack(it.input) }
                    extractor.seekTo((originUs - part.startedAtMs * 1000).coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val byInput = tracks.associateBy { it.input }
                    val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
                    val info = MediaCodec.BufferInfo()
                    val segmentEnds = mutableMapOf<String, Long>()
                    while (extractor.sampleTrackIndex >= 0) {
                        val track = byInput[extractor.sampleTrackIndex] ?: error("实况轨道无效")
                        val absolute = window.absoluteUs(part, extractor.sampleTime)
                        if (absolute >= originUs && absolute <= window.endMs * 1000 && absolute > (priorEnds[track.kind] ?: -1)) {
                            require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "不支持加密实况片段" }
                            if (Build.VERSION.SDK_INT >= 28) require(extractor.sampleSize in 1..buffer.capacity().toLong()) { "实况单帧过大" }
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            require(size in 1..buffer.capacity()) { "实况片段读取失败" }
                            val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            info.set(0, size, absolute - originUs, flags)
                            requireNotNull(muxer).writeSampleData(outputTracks.getValue(track.kind), buffer, info)
                            segmentEnds[track.kind] = maxOf(segmentEnds[track.kind] ?: -1, absolute)
                            if (track.kind == "video") finalUs = maxOf(finalUs, absolute)
                        }
                        if (!extractor.advance()) break
                    }
                    segmentEnds.forEach { (kind, end) -> priorEnds[kind] = maxOf(priorEnds[kind] ?: -1, end) }
                } finally { extractor.release() }
            }
            require(finalUs > shutterAtMs * 1000 && originUs <= shutterAtMs * 1000) { "实况视频没有完整捕获快门前后画面" }
            requireNotNull(muxer).stop(); started = false
            muxer?.release(); muxer = null
            check(stage.length() > 0) { "实况输出为空" }
            Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            val duration = (finalUs - originUs + frameStepUs) / 1000
            return CameraMotionVideo(duration, rotation, ((shutterAtMs * 1000 - originUs) / 1000).coerceIn(0, duration))
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            stage.delete()
        }
    }

    private fun tracks(extractor: MediaExtractor): List<Track> = (0 until extractor.trackCount).mapNotNull { index ->
        val format = extractor.getTrackFormat(index)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        when { mime.startsWith("video/") -> Track(index, "video", format)
            mime.startsWith("audio/") -> Track(index, "audio", format)
            else -> null }
    }.also { tracks ->
        require(tracks.count { it.kind == "video" } == 1 && tracks.count { it.kind == "audio" } <= 1) { "实况轨道格式无效" }
    }.sortedBy { it.kind }

    private fun formatsMatch(reference: List<Track>, candidate: List<Track>): Boolean =
        reference.map { it.kind } == candidate.map { it.kind } && reference.zip(candidate).all { (a, b) ->
            cameraMotionFormatsMatch(a.format, b.format)
        }
}

/** Identical decoder configuration is required before two recording generations can be concatenated. */
internal fun cameraMotionFormatsMatch(a: MediaFormat, b: MediaFormat): Boolean {
    val keys = listOf(MediaFormat.KEY_MIME, MediaFormat.KEY_WIDTH, MediaFormat.KEY_HEIGHT,
        MediaFormat.KEY_SAMPLE_RATE, MediaFormat.KEY_CHANNEL_COUNT, MediaFormat.KEY_COLOR_STANDARD,
        MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.KEY_COLOR_RANGE, MediaFormat.KEY_PROFILE)
    if (keys.any { key -> a.containsKey(key) != b.containsKey(key) || (a.containsKey(key) &&
            if (key == MediaFormat.KEY_MIME) a.getString(key) != b.getString(key) else a.getInteger(key) != b.getInteger(key)) }) return false
    return (0..2).all { index ->
        fun bytes(format: MediaFormat): ByteArray? = format.getByteBuffer("csd-$index")?.duplicate()?.let { buffer ->
            buffer.position(0); ByteArray(buffer.remaining()).also(buffer::get)
        }
        val first = bytes(a); val second = bytes(b)
        if (first == null || second == null) first == null && second == null else first.contentEquals(second)
    }
}
