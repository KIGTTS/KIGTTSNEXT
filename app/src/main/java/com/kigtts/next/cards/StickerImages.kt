package com.kigtts.next.cards

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.kigtts.next.media.MediaKind
import com.kigtts.plugin.api.*
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Import before inspection, so later source mutation cannot alter an approved image. */
internal class StickerImages(private val owner:StickerRepository) {
    fun import(uri:Uri):StickerImage=import(requireNotNull(owner.context.contentResolver.openInputStream(uri)) {"无法读取图片"})
    fun import(input:InputStream):StickerImage {
        val id=UUID.randomUUID().toString();val directory=File(owner.root,"images/$id").apply {mkdirs()};val file=File(directory,"source")
        try {
            input.use {source->file.outputStream().use {target->var count=0L;val buffer=ByteArray(65536)
                while(true) {val size=source.read(buffer);if(size<0)break;count+=size;stickerRequire(count<=MAX_BYTES,StickerError.TOO_LARGE,"图片不能超过 32 MB");target.write(buffer,0,size)}}}
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            file.inputStream().use {stream->BitmapFactory.decodeStream(stream,null,bounds)}
            stickerRequire(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth<=32768&&bounds.outHeight<=32768&&bounds.outWidth.toLong()*bounds.outHeight<=80_000_000,message="请选择可读取的图片")
            val mime=bounds.outMimeType.orEmpty();stickerRequire(mime in supported,message="此图片格式暂不支持")
            val (width, height) = stickerDisplayDimensions(file, bounds.outWidth, bounds.outHeight)
            return owner.addImage(StickerImage(id,mime,file.length(),width,height,System.currentTimeMillis()))
        } catch(error:OutOfMemoryError) {directory.deleteRecursively();throw StickerFault(StickerError.TOO_LARGE,"设备暂时无法读取这张图片，请换一张较小的图片")}
        catch(error:Exception) {directory.deleteRecursively();throw error}
    }
    fun file(id:String):File? {
        if(owner.images.value.none {it.id==id})return null
        // The ID comes from the validated catalog, never a caller-controlled filesystem path.
        return File(owner.root,"images/$id/source").canonicalFile.takeIf {it.isFile&&it.toPath().startsWith(owner.root.canonicalFile.toPath())}
    }
    fun importMedia(id:String):StickerImage {
        val entry=owner.media.get(id) ?: throw StickerFault(StickerError.NOT_FOUND,"快捷文本图片不存在")
        stickerRequire(entry.kind in setOf(MediaKind.IMAGE,MediaKind.MOTION_PHOTO),message="请选择图片或实况照片")
        return import((owner.media.file(entry) ?: throw StickerFault(StickerError.NOT_FOUND,"图片已无法读取")).inputStream())
    }
    fun quickMedia(id:String):QuickMedia {
        val image=owner.images.value.firstOrNull {it.id==id} ?: throw StickerFault(StickerError.NOT_FOUND,"贴纸图片不存在")
        val file=file(id) ?: throw StickerFault(StickerError.NOT_FOUND,"贴纸图片已无法读取")
        val result=owner.media.import(Uri.fromFile(file),image.mime)
        return QuickMedia(result.id,result.kind.name,result.pluginId,result.created,scale=result.scale.name,mime=result.mimeType,
            thumbnailMode=result.thumbnailMode.name,focusX=result.focusX,focusY=result.focusY,motion=result.video!=null,appleLive=result.appleLive)
    }
    companion object {
        const val MAX_BYTES=32L*1024*1024
        private val supported=setOf("image/jpeg","image/png","image/webp","image/gif","image/bmp","image/heif","image/heic","image/avif")
    }
}

/** Match the orientation used by the photo renderer without allocating or rewriting the original. */
internal fun stickerDisplayDimensions(file: File, encodedWidth: Int, encodedHeight: Int): Pair<Int, Int> {
    val rotation = runCatching { ExifInterface(file).rotationDegrees }.getOrDefault(0)
    return if (rotation == 90 || rotation == 270) encodedHeight to encodedWidth else encodedWidth to encodedHeight
}
