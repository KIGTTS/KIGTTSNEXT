package com.kigtts.next.cards

import android.media.MediaMetadataRetriever
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CardMotionClip(val imageId: String, val video: File, val durationMs: Long, val rotationDegrees: Int,
    val shutterPositionMs: Long, val colorMatrix: FloatArray)

/** Attach to immutable image IDs: an unsaved camera retake never replaces a saved design's motion. */
class CardMotionRepository(private val stickers: StickerRepository) {
    private val root = File(stickers.root, "motion")
    @Synchronized fun import(imageId: String, source: File, durationMs: Long, rotationDegrees: Int = 0,
        shutterPositionMs: Long = 0, colorMatrix: FloatArray = identity()): CardMotionClip {
        require(stickers.images.value.any { it.id == imageId }) { "实况照片不存在" }
        require(durationMs in 1..30000 && rotationDegrees in listOf(0, 90, 180, 270) &&
            shutterPositionMs in 0..durationMs && colorMatrix.size == 20 && colorMatrix.all(Float::isFinite)) { "实况参数无效" }
        require(source.isFile && source.length() in 16..128L * 1024 * 1024) { "实况视频无效" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(source.absolutePath)
            require((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0) > 0) { "实况视频不可播放" }
        } finally { retriever.release() }
        check(root.isDirectory || root.mkdirs()) { "无法保存实况照片" }
        val directory = File(root, imageId).canonicalFile
        require(directory.parentFile == root.canonicalFile) { "实况标识无效" }
        require(!directory.exists()) { "此照片已有关联的实况" }
        val pending = File(root, ".pending-" + UUID.randomUUID())
        check(pending.mkdir()) { "无法保存实况照片" }
        try {
            source.inputStream().use { input -> File(pending, "motion.mp4").outputStream().use { input.copyTo(it) } }
            File(pending, "motion.json").writeText(JSONObject().apply {
                put("schema", 1); put("imageId", imageId); put("durationMs", durationMs); put("rotationDegrees", rotationDegrees)
                put("shutterPositionMs", shutterPositionMs); put("colorMatrix", JSONArray(colorMatrix.toList()))
            }.toString(), Charsets.UTF_8)
            check(pending.renameTo(directory)) { "无法完成实况保存" }
            return CardMotionClip(imageId, File(directory, "motion.mp4"), durationMs, rotationDegrees, shutterPositionMs, colorMatrix.copyOf())
        } finally { pending.deleteRecursively() }
    }
    @Synchronized fun get(imageId: String?): CardMotionClip? {
        if (imageId == null || stickers.images.value.none { it.id == imageId }) return null
        return runCatching {
            val directory = File(root, imageId).canonicalFile
            require(directory.parentFile == root.canonicalFile)
            val json = JSONObject(File(directory, "motion.json").readText(Charsets.UTF_8))
            require(json.getInt("schema") == 1 && json.getString("imageId") == imageId)
            val matrix = json.getJSONArray("colorMatrix"); require(matrix.length() == 20)
            val file = File(directory, "motion.mp4"); require(file.isFile)
            CardMotionClip(imageId, file, json.getLong("durationMs"), json.getInt("rotationDegrees"),
                json.getLong("shutterPositionMs"), FloatArray(20) { matrix.getDouble(it).toFloat().also { value -> require(value.isFinite()) } })
        }.getOrNull()
    }
    /** Remove only unreferenced attachments; copying a design intentionally shares its source. */
    @Synchronized fun prune() {
        val used = stickers.images.value.map { it.id }.toSet()
        root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") && it.name !in used }.forEach {
            if (it.canonicalFile.parentFile == root.canonicalFile) it.deleteRecursively()
        }
    }
    companion object {
        fun identity() = floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)
    }
}
