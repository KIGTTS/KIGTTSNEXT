package com.kigtts.next

import android.graphics.BitmapFactory
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.media3.ui.PlayerView
import com.kigtts.next.ui.media.videoFrameSnapshot
import android.view.LayoutInflater
import android.view.TextureView
import android.widget.FrameLayout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import com.kigtts.next.media.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native decoder and WebView checks use generated test-only JPEG/H.264 fixtures, with no network. */
@RunWith(AndroidJUnit4::class)
class MediaDeviceTest {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun videoBackdropReadsActualTextureAsynchronouslyAndBlursWithoutAnotherDecoder() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir,"frame-device-" + java.util.UUID.randomUUID() + ".mp4")
        instrumentation.context.assets.open("media/video.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var player: ExoPlayer? = null; var view: PlayerView? = null
        val rendered = CountDownLatch(1)
        try {
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                view = LayoutInflater.from(activity).inflate(R.layout.media_player,root,false) as PlayerView
                root.addView(view); activity.setContentView(root)
                player = ExoPlayer.Builder(activity).build().apply {
                    volume = 0f; addListener(object : Player.Listener { override fun onRenderedFirstFrame() { rendered.countDown() } })
                    view!!.player = this; setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file))); prepare(); play()
                }
            }
            assertTrue("First video frame not rendered",rendered.await(15,TimeUnit.SECONDS))
            val frame = withContext(Dispatchers.Main) { videoFrameSnapshot(requireNotNull(view!!.videoSurfaceView as? TextureView)) }
            assertNotNull("PixelCopy cannot read the actual video texture",frame)
            val blurred = GaussianBackdrop.blur(frame!!); assertTrue(blurred.width<=128);assertTrue(blurred.height<=128)
            assertEquals(255,android.graphics.Color.alpha(blurred.getPixel(blurred.width/2,blurred.height/2)))
            blurred.recycle();frame.recycle()
        } finally { scenario.onActivity { player?.release() }; scenario.close();file.delete() }
    }
    @Test fun androidMotionPhotoDecodesStillAndExtractedVideoPlaysToEnd() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "media-device-" + java.util.UUID.randomUUID()).apply { mkdirs() }
        var player: ExoPlayer? = null
        try {
            val file = File(directory, "motion-photo.jpg")
            instrumentation.context.assets.open("media/motion-photo.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path)); assertEquals(160, bitmap.width); bitmap.recycle()
            val clip = File(directory, "clip.mp4"); assertTrue(MotionPhoto.extract(file, clip))
            val ended = CountDownLatch(1); var failure: PlaybackException? = null
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(instrumentation.targetContext).build().apply {
                    volume = 0f; addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) ended.countDown() }
                        override fun onPlayerError(error: PlaybackException) { failure = error; ended.countDown() }
                    }); setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(clip))); prepare(); play()
                }
            }
            assertTrue("Native playback timed out", ended.await(15, TimeUnit.SECONDS)); assertNull(failure)
        } finally { instrumentation.runOnMainSync { player?.release() }; directory.deleteRecursively() }
    }
    @Test fun localPluginWebMessageReachesOnlyItsDeclaredAction() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "web-device-" + java.util.UUID.randomUUID()).apply { mkdirs() }
        val entry = MediaEntry("id", "测试网页", MediaKind.HTML, "index.html", pluginId = "test.web", actions = listOf("click"))
        File(directory, "index.html").writeText("<html><body><script>Kigtts.postMessage(JSON.stringify({action:'click',inputs:{value:'ok'}}))</script></body></html>")
        val received = CountDownLatch(1); var callback: Pair<String, Map<String,String>>? = null; var web: WebView? = null
        try {
            instrumentation.runOnMainSync {
                web = WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false
                    val loader = WebResourceLoader(File(directory,"cache"), directory, false)
                    webViewClient = object : WebViewClient() { override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = loader.load(request.url.toString(), request.method) }
                    WebViewCompat.addWebMessageListener(this,"Kigtts",setOf(WebContentPolicy.ORIGIN)) { _, message, origin, mainFrame, _ ->
                        callback = WebContentPolicy.action(entry,message.data.orEmpty(),origin.toString(),mainFrame); received.countDown()
                    }
                    loadUrl(WebContentPolicy.ORIGIN + "/content/index.html")
                }
            }
            assertTrue("WebView message timed out", received.await(15,TimeUnit.SECONDS)); assertEquals("click" to mapOf("value" to "ok"),callback)
        } finally { instrumentation.runOnMainSync { web?.destroy() }; directory.deleteRecursively() }
    }
}
