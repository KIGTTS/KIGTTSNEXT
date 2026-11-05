package com.kigtts.next.cards

import android.graphics.*
import com.kigtts.next.media.GaussianBackdrop
import com.kigtts.plugin.api.PhotoBackdropMode
import com.kigtts.plugin.api.PolaroidCard

/** Foreground pose is measured against the photograph window; backdrop always fills that window. */
internal object PolaroidPhotoRenderer {
    fun draw(canvas:Canvas,source:Bitmap,window:RectF,paint:Paint,card:PolaroidCard) {
        PhotoFrameSpec.from(card.frameStyleId)?.crop?.let { crop ->
            val (left, top, right, bottom) = crop.pixels(source.width, source.height)
            canvas.drawBitmap(source, Rect(left, top, right, bottom), window, paint)
            return
        }
        if(card.photoBackdrop==PhotoBackdropMode.BLUR) {
            val backdrop=GaussianBackdrop.blur(source,maximum=128,sigma=5f)
            try {val scale=maxOf(window.width()/backdrop.width,window.height()/backdrop.height)
                val width=backdrop.width*scale;val height=backdrop.height*scale
                canvas.drawBitmap(backdrop,null,RectF(window.centerX()-width/2,window.centerY()-height/2,
                    window.centerX()+width/2,window.centerY()+height/2),paint)
            } finally {backdrop.recycle()}
        }
        val scale=(minOf(window.width()/source.width,window.height()/source.height)*card.photoZoom).toFloat()
        val width=source.width*scale;val height=source.height*scale
        val x=window.left+(window.width()*card.photoX).toFloat();val y=window.top+(window.height()*card.photoY).toFloat()
        canvas.drawBitmap(source,null,RectF(x-width/2,y-height/2,x+width/2,y+height/2),paint)
    }
}
