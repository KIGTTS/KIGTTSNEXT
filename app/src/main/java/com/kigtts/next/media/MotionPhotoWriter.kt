package com.kigtts.next.media

import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Android Motion Photo: one real JPEG primary image followed by one complete ISO-BMFF video. */
object MotionPhotoWriter {
    fun write(still: File, video: File, output: File, presentationTimestampUs: Long = -1): File {
        require(output.name.endsWith("MP.jpg", true)) { "实况照片文件名须以 MP.jpg 结尾" }
        require(still.canonicalFile != output.canonicalFile && video.canonicalFile != output.canonicalFile)
        require(video.length() in 16..256L * 1024 * 1024 && presentationTimestampUs >= -1)
        video.inputStream().use { input ->
            val head = ByteArray(8); require(input.read(head) == 8 && String(head, 4, 4, Charsets.US_ASCII) == "ftyp") { "实况视频格式无效" }
        }
        val bytes = ("http://ns.adobe.com/xap/1.0/\u0000" + xmp(video.length(), presentationTimestampUs)).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65533)
        check(output.parentFile?.let { it.isDirectory || it.mkdirs() } != false)
        val stage = File(output.parentFile, ".motion-" + UUID.randomUUID() + ".tmp")
        try {
            DataOutputStream(stage.outputStream().buffered()).use { target ->
                still.inputStream().buffered().use { photo ->
                    require(photo.read() == 0xFF && photo.read() == 0xD8) { "实况封面必须是 JPEG" }
                    target.writeShort(0xFFD8); target.writeShort(0xFFE1); target.writeShort(bytes.size + 2); target.write(bytes)
                    photo.copyTo(target)
                }
                video.inputStream().use { it.copyTo(target) }
            }
            require(MotionPhoto.locate(stage)?.length == video.length()) { "实况封装验证失败" }
            Files.move(stage.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return output
        } finally { stage.delete() }
    }
    private fun xmp(length: Long, timestamp: Long) = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:GCamera="http://ns.google.com/photos/1.0/camera/" xmlns:Container="http://ns.google.com/photos/1.0/container/" xmlns:Item="http://ns.google.com/photos/1.0/container/item/" GCamera:MotionPhoto="1" GCamera:MotionPhotoVersion="1" GCamera:MotionPhotoPresentationTimestampUs="$timestamp" GCamera:MicroVideoOffset="$length"><Container:Directory><rdf:Seq><rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/></rdf:li><rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$length" Item:Padding="0"/></rdf:li></rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>"""
}
