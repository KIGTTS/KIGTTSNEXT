package com.kigtts.next.cards

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.kigtts.next.R
import com.kigtts.plugin.api.*
import java.io.File
import kotlin.math.roundToInt

/** Shared export geometry: overlays are positioned against the complete paper canvas. */
internal object PolaroidRenderer {
    fun export(repository:StickerRepository,card:PolaroidCard,options:PolaroidExportOptions=PolaroidExportOptions(),maximumHeight:Int=1500):File=try {render(repository,card,options,maximumHeight)}
        catch(_:OutOfMemoryError) {throw StickerFault(StickerError.TOO_LARGE,"设备内存不足，请稍后重试导出")}
    private fun render(repository:StickerRepository,card:PolaroidCard,options:PolaroidExportOptions,maximumHeight:Int):File {
        val result=renderBitmap(repository,card,options,maximumHeight=maximumHeight)
        try {
            val suffix=if(options.includeFrame&&options.includeStickers) "" else "-${options.includeFrame}-${options.includeStickers}"
            val resolution=if(maximumHeight>1500) "-hd" else ""
            val output=File(repository.root,"exports/${card.id}$suffix$resolution.png").apply {parentFile!!.mkdirs()}
            val stage=File(output.parentFile,".${card.id}-${java.util.UUID.randomUUID()}.tmp")
            try {stage.outputStream().use {stickerRequire(result.compress(Bitmap.CompressFormat.PNG,100,it),message="拍立得导出失败")}
                java.nio.file.Files.move(stage.toPath(),output.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);return output
            } finally {stage.delete()}
        } finally {result.recycle()}
    }
    /** A transparent photo window is reused as a single immutable overlay by the GPU movie renderer. */
    fun renderBitmap(repository:StickerRepository,card:PolaroidCard,options:PolaroidExportOptions=PolaroidExportOptions(),
        transparentPhoto:Boolean=false,maximumHeight:Int=1500, beforeLayers: ((Canvas, Bitmap) -> Unit)? = null):Bitmap {
        val frame=repository.frameStyle(card);val geometry=polaroidPaperGeometry(card,frame)
        val branding=polaroidBranding(card,frame)
        val captionWidth=branding?.captionWidth ?: geometry.titleWidth
        // Gallery exports use source pixels rather than the 1500px sharing/preview ceiling.
        val limit=minOf(6144.0/maxOf(1.0,geometry.aspectRatio),kotlin.math.sqrt(12_000_000.0/geometry.aspectRatio)).toInt()
        val height=maximumHeight.coerceIn(128,limit);val width=(height*geometry.aspectRatio).roundToInt();val unit=(height/geometry.paperHeight).toFloat()
        val decodeEdge=if(maximumHeight>1500)8192 else 2048
        val result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
        val paperColor=polaroidPaperColor(card,frame).toInt()
        val canvas=Canvas(result);canvas.drawColor(paperColor);val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val imageArea=RectF((geometry.photoLeft*unit).toFloat(),(geometry.photoTop*unit).toFloat(),
            (geometry.photoRight*unit).toFloat(),(geometry.photoBottom*unit).toFloat())
        val radius=(frame.photoCornerRadius*width).toFloat()
        val photoPath=Path().apply {addRoundRect(imageArea,radius,radius,Path.Direction.CW)}
        val watermark = PhotoFrameSpec.from(card.frameStyleId)?.watermark?.takeIf { options.includeFrame }
        val glassStyle = watermark?.takeIf { it.palette == WatermarkPalette.GLASS && card.imageId != null }
        canvas.save();canvas.clipPath(photoPath)
        if(transparentPhoto && glassStyle == null)canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR) else {
            if(card.imageId!=null || !isFullPhotoPaper(card))canvas.drawColor(0xFFB0B0B0.toInt())
            card.imageId?.let {id->decode(repository,id,decodeEdge)?.let {bitmap->try {
                renderCardPhotoStyle(bitmap,card.photoStyle,repository::imageFile)
                PolaroidPhotoRenderer.draw(canvas,bitmap,imageArea,paint,card)
            } finally {bitmap.recycle()}}}
        };canvas.restore()
        if (glassStyle != null) {
            // Bake from the already positioned and treated still. Motion uses the same frozen paper.
            PhotoFrameGlass.prepare(result, imageArea.framePixelBounds(result), glassStyle.position, glassStyle.band, glassStyle.edge > 0).use {
                it.draw(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), imageArea)
            }
            if (transparentPhoto) {
                canvas.save(); canvas.clipPath(photoPath)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); canvas.restore()
            }
        }
        if(options.includeFrame&&frame.borderWidth>0&&frame.borderColor!=null) {
            val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;color=requireNotNull(frame.borderColor).toInt();strokeWidth=(frame.borderWidth*width).toFloat()}
            canvas.drawPath(photoPath,line)
        }
        if(options.includeFrame)frame.overlayImageId?.let {id->decode(repository,id,decodeEdge)?.let {bitmap->try {canvas.save()
            if (PhotoFrameSpec.from(card.frameStyleId) != null) canvas.clipOutRect(imageArea)
            canvas.drawBitmap(bitmap,null,RectF(0f,0f,width.toFloat(),height.toFloat()),paint)
            canvas.restore()} finally {bitmap.recycle()}}}
        val dark=Color.luminance(paperColor)<.45f
        if(options.includeFrame) {
        watermark?.let { style ->
            drawCameraWatermark(repository.context, canvas, style, geometry,
                watermarkFacts((card.originalImageId ?: card.imageId)?.let(repository::imageFile), repository.designs.facts(card.id)),
                width.toFloat(), height.toFloat())
        }
        if(card.layers.any {it.kind==CardLayerKind.PHOTO_INFO})polaroidCaption(card,geometry,captionWidth,width.toFloat(),
            if(dark)Color.WHITE else 0xFF202020.toInt(),if(dark)0xFFDDDDDD.toInt() else 0xFF4A4A4A.toInt()).draw(canvas)
        else {
        drawText(canvas,card.title,(geometry.titleX*unit).toFloat(),(geometry.titleY*unit).toFloat(),
            (captionWidth*unit).roundToInt(),(geometry.titleSize*unit).toFloat(),if(dark)Color.WHITE else 0xFF202020.toInt(),true)
        drawText(canvas,card.description,(geometry.titleX*unit).toFloat(),(geometry.descriptionY*unit).toFloat(),
            (captionWidth*unit).roundToInt(),(geometry.descriptionSize*unit).toFloat(),if(dark)0xFFDDDDDD.toInt() else 0xFF4A4A4A.toInt(),false)
        }
        branding?.let { box ->
            repository.context.getDrawable(R.drawable.logo_horizontal)?.mutate()?.let { logo ->
                logo.setTint(if(dark) Color.WHITE else 0xFF202020.toInt())
                logo.setBounds((box.x*unit).roundToInt(),(box.y*unit).roundToInt(),
                    ((box.x+box.width)*unit).roundToInt(),((box.y+box.height)*unit).roundToInt())
                logo.draw(canvas)
            }
        }
        }
        beforeLayers?.invoke(canvas, result)
        if(options.includeStickers)card.layers.filter {it.kind!=CardLayerKind.PHOTO_INFO}.forEach {layer->
            val x=(layer.x*width).toFloat();val y=(layer.y*height).toFloat();val span=(layer.width*width).toFloat()
            canvas.save();canvas.rotate(layer.rotation.toFloat(),x,y)
            canvas.scale(if(layer.flipX)-1f else 1f,if(layer.flipY)-1f else 1f,x,y)
            when(layer.kind) {
                CardLayerKind.IMAGE->layer.imageId?.let {id->decode(repository,id,decodeEdge)?.let {bitmap->try {
                    val tall=span*bitmap.height/bitmap.width;canvas.drawBitmap(bitmap,null,RectF(x-span/2,y-tall/2,x+span/2,y+tall/2),paint)
                } finally {bitmap.recycle()}}}
                CardLayerKind.TEXT->{val text=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=layer.color.toInt();textSize=span}
                    val layout=StaticLayout.Builder.obtain(layer.text,0,layer.text.length,text,width).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(0f,1.2f).build()
                    canvas.translate(x-width/2f,y-layout.height/2f);layout.draw(canvas)}
                CardLayerKind.PHOTO_INFO -> Unit // Caption fields belong to paper, not draggable stickers.
            };canvas.restore()
        }
        if(options.includeFrame)return result
        val left=imageArea.left.roundToInt().coerceIn(0,width-1);val top=imageArea.top.roundToInt().coerceIn(0,height-1)
        val clipped=Bitmap.createBitmap(result,left,top,imageArea.width().roundToInt().coerceIn(1,width-left),
            imageArea.height().roundToInt().coerceIn(1,height-top))
        if(clipped!==result)result.recycle()
        return clipped
        } catch(failure:Throwable) {result.recycle();throw failure}
    }
    private fun decode(repository:StickerRepository,id:String,maximumEdge:Int=2048):Bitmap?=repository.imageFile(id)?.let {file->
        val image=repository.images.value.firstOrNull {it.id==id} ?: return@let null
        var sample=1;while(maxOf(image.width,image.height)/sample>maximumEdge || image.width.toLong()*image.height/sample/sample>24_000_000)sample*=2
        val bitmap=file.inputStream().use {stream->BitmapFactory.decodeStream(stream,null,BitmapFactory.Options().apply {inSampleSize=sample;inMutable=true})} ?: return@let null
        val exif=runCatching {file.inputStream().use {ExifInterface(it)}}.getOrNull()
        val matrix=Matrix().apply {exif?.let {if(it.isFlipped)postScale(-1f,1f);postRotate(it.rotationDegrees.toFloat())}}
        try {Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).let { rotated ->
            if(rotated!==bitmap)bitmap.recycle()
            if(rotated.isMutable)rotated else rotated.copy(Bitmap.Config.ARGB_8888,true).also {rotated.recycle()}
        }}
        catch(error:Throwable) {bitmap.recycle();throw error}}
    private fun drawText(canvas:Canvas,value:String,x:Float,y:Float,width:Int,size:Float,color:Int,bold:Boolean) {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=size;this.color=color;typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT}
        val layout=StaticLayout.Builder.obtain(value,0,value.length,paint,width).setIncludePad(false).setLineSpacing(0f,1.2f).setMaxLines(if(bold)1 else 3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore()
    }
}
