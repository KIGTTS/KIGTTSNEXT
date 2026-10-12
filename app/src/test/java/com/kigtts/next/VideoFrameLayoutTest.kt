package com.kigtts.next

import com.kigtts.next.media.MediaScale
import com.kigtts.next.ui.media.videoFrameScale
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import org.junit.Test
import org.junit.Assert.*

class VideoFrameLayoutTest {
    @Test fun sharpOutputIsUniformForPortraitLandscapeAndRapidlyChangingCardHeights() {
        listOf(4f/3f,16f/9f,9f/16f,1f).forEach { ratio ->
            listOf(1080 to 1900,1080 to 350,1900 to 700,300 to 120,300 to 800).forEach { (w,h) ->
                MediaScale.entries.forEach { mode ->
                    val source=IntSize((ratio*1440).toInt(),1440)
                    val factor=videoFrameScale(w,h,source,mode)
                    val width=source.width*factor;val height=source.height*factor
                    assertEquals(ratio,width/height,.015f)
                    assertTrue("The content must touch at least one viewport edge",abs(width-w)<.001f||abs(height-h)<.001f)
                    if(mode==MediaScale.FIT)assertTrue(width<=w+.001f&&height<=h+.001f)
                    else assertTrue(width>=w-.001f&&height>=h-.001f)
                }
            }
        }
    }
}
