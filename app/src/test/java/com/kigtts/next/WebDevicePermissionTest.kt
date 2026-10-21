package com.kigtts.next

import android.app.Application
import android.Manifest
import android.net.Uri
import android.webkit.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.plugin.runtime.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class WebDevicePermissionTest {
    private class Request(private val site: String, private val resource: String) : PermissionRequest() {
        var granted = false; var denied = false
        override fun getOrigin(): Uri = Uri.parse(site)
        override fun getResources() = arrayOf(resource)
        override fun grant(resources: Array<out String>) { granted = resources.contentEquals(arrayOf(resource)) }
        override fun deny() { denied = true }
    }
    @Test fun cameraAndMicrophoneAreRequestedOnlyOnUseAndRemainBoundToEntryOriginAndActivePage() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("plugin-permissions", 0).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val ledger = PluginPermissions(app).apply { foreground = true }; val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val web = WebView(app).apply { loadUrl("https://example.com/page") }; var active = true
        val privileges = WebPagePrivileges(web, ledger, "entry-one", "测试页面", scope, { active }, { true })
        privileges.install(); assertTrue(ledger.pending.value.isEmpty())
        val camera = Request("https://example.com", PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        web.webChromeClient!!.onPermissionRequest(camera)
        assertFalse(camera.granted); val prompt = ledger.pending.value.single(); assertEquals("camera", prompt.permission)
        ledger.decide(prompt.token, PermissionAccess.ALLOW); yield(); assertTrue(camera.granted)
        val microphone = Request("https://example.com", PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        web.webChromeClient!!.onPermissionRequest(microphone); ledger.decide(ledger.pending.value.single().token, PermissionAccess.DENY); yield(); assertTrue(microphone.denied)
        val crossOrigin = Request("https://another.example", PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        web.webChromeClient!!.onPermissionRequest(crossOrigin); yield(); assertTrue(crossOrigin.denied); assertTrue(ledger.pending.value.isEmpty())
        val second = WebPagePrivileges(web, ledger, "entry-two", "另一词条", scope, { active }, { true }); second.install()
        val separate = Request("https://example.com", PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        web.webChromeClient!!.onPermissionRequest(separate); assertEquals(1, ledger.pending.value.size); second.pause(); yield(); assertTrue(separate.denied)
        active = false; val hidden = Request("https://example.com", PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        web.webChromeClient!!.onPermissionRequest(hidden); yield(); assertTrue(hidden.denied); assertTrue(ledger.pending.value.isEmpty())
        privileges.close(); second.close(); scope.cancel(); web.destroy()
    }
}
