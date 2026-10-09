package com.kigtts.next

import com.kigtts.next.media.MediaScale
import com.kigtts.next.ui.media.videoFrameGeometry
import org.junit.Assert.*
import org.junit.Test

class VideoFrameGeometryTest {
    @Test fun resizeFramesUseTheirOwnRoundedFramebufferAspectAndRetainTheirScaleMode() {
        val frames=listOf(.55f,1.7f,.23f,3.2f,.9f).map { videoFrameGeometry(720,it,MediaScale.FIT) }
        frames.forEach {
            assertEquals(720,maxOf(it.width,it.height));assertEquals(it.width.toFloat()/it.height,it.aspect,0f)
        }
        val first=frames.first()
        videoFrameGeometry(720,2f,MediaScale.FILL)
        assertEquals(MediaScale.FIT,first.scale);assertEquals(396,first.width);assertEquals(720,first.height)
    }
    @Test fun narrowAndWideWindowsDoNotUseTheOldAspectClamp() {
        val narrow=videoFrameGeometry(1440,.02f,MediaScale.FIT)
        val wide=videoFrameGeometry(1440,40f,MediaScale.FILL)
        assertEquals(.02f,narrow.aspect,.001f);assertEquals(40f,wide.aspect,.01f)
        assertTrue(narrow.width>0&&wide.height>0)
    }
    @Test fun invalidOrZeroSizeKeepsAUsableFrame() {
        listOf(Float.NaN,Float.POSITIVE_INFINITY,0f,-1f).forEach {
            val frame=videoFrameGeometry(0,it,MediaScale.FIT)
            assertEquals(1,frame.width);assertEquals(1,frame.height);assertEquals(1f,frame.aspect,0f)
        }
    }
}
