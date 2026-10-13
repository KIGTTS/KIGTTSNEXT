package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35])
class PolaroidBrandingTest {
    private fun repository():StickerRepository {
        val app=ApplicationProvider.getApplicationContext<Application>();val paths=PluginPaths(app)
        return StickerRepository(app,paths,MediaRepository(app,paths))
    }
    private fun bitmap(repo:StickerRepository,card:PolaroidCard):Bitmap=repo.exportCard(card).inputStream().use {requireNotNull(BitmapFactory.decodeStream(it))}
    private fun nonWhite(image:Bitmap,x:Double,y:Double,width:Double,height:Double,unit:Double):Int {
        var count=0
        for(py in floor(y*unit).toInt() until ceil((y+height)*unit).toInt())
            for(px in floor(x*unit).toInt() until ceil((x+width)*unit).toInt())
                if(image.getPixel(px,py)!=Color.WHITE)count++
        return count
    }
    @Test fun fourClassicFormatsExportRealLogoPixelsWithPureWhitePaperAndASeparateCaptionLane() {
        val repo=repository()
        listOf("classic","instax.mini.classic","instax.square.classic","instax.wide.classic").forEach {id->
            val card=PolaroidCard(title="很长的标题".repeat(12),description="正文".repeat(80),frameStyleId=id)
            val frame=repo.frameStyle(card);val geometry=frame.format.geometry;val logo=requireNotNull(polaroidBranding(card,frame))
            val image=bitmap(repo,card);val unit=image.height/geometry.paperHeight
            try {
                assertEquals(Color.WHITE,image.getPixel(10,10))
                assertTrue("Logo pixels missing for $id",nonWhite(image,logo.x,logo.y,logo.width,logo.height,unit)>40)
                assertTrue(logo.y>=geometry.photoBottom);assertTrue(logo.y+logo.height<geometry.paperHeight)
                val gapX=geometry.titleX+logo.captionWidth+.2*(logo.x-geometry.titleX-logo.captionWidth)
                val gapWidth=(logo.x-gapX)*.6
                assertEquals("Caption leaked into logo lane for $id",0,nonWhite(image,gapX,logo.y,gapWidth,logo.height,unit))
            } finally {image.recycle()}
        }
    }
    @Test fun customBrandedMissingAndNonClassicFramesNeverReceiveForcedHostBranding() {
        val repo=repository()
        val custom=PolaroidFrameStyle("brand.paper","合作款",owner="brand",format=PolaroidFormat.SQUARE)
        repo.apply(listOf(StickerEdit.PutFrameStyle(custom)))
        val host=PolaroidCard(title="拍立得",frameStyleId="instax.square.classic")
        val logo=requireNotNull(polaroidBranding(host,repo.frameStyle(host)))
        val branded=host.copy(frameStyleId=custom.id)
        assertNull(polaroidBranding(branded,custom));assertNull(polaroidBranding(host.copy(frameStyleId="brand.missing"),repo.frameStyle(host)))
        assertNull(polaroidBranding(host.copy(frameStyleId="instax.square.slim"),repo.frameStyle(host.copy(frameStyleId="instax.square.slim"))))
        val image=bitmap(repo,branded)
        try {assertEquals(0,nonWhite(image,logo.x,logo.y,logo.width,logo.height,image.height/PolaroidFormat.SQUARE.geometry.paperHeight))}
        finally {image.recycle()}
    }
}
