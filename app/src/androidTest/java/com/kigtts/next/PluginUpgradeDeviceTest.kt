package com.kigtts.next

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Install same-publisher API9 packages without uninstalling models or changing existing enablement. */
@RunWith(AndroidJUnit4::class)
class PluginUpgradeDeviceTest {
    @Test fun upgradeSignedPackagesAndKeepExistingData() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = PluginManager(instrumentation.targetContext, emptyList())
        val previous = manager.packages.installed().associate { it.manifest.id to it.enabled }
        try {
            for (id in listOf("engine.sensevoice", "engine.piper", "service.speech-synthesis", "engine.streaming-asr", "service.speech-recognition")) {
                val input = File(manager.paths.packages,".api9-upgrade-$id.kigplugin")
                instrumentation.context.assets.open("$id-0.8.0.kigplugin").use { source -> input.outputStream().use { source.copyTo(it) } }
                try {
                    val preview = manager.packages.inspect(input)
                    if (manager.packages.installed().none { it.manifest.id==id && it.manifest.versionCode==preview.manifest.versionCode }) manager.install(input, preview.publisherSha256)
                    previous[id]?.let { manager.enable(id,it) }
                } finally { input.delete() }
            }
            manager.reload();assertTrue(manager.status.toString(),manager.status.all { it.error==null })
            val upgraded = setOf("engine.sensevoice", "engine.piper", "service.speech-synthesis", "engine.streaming-asr", "service.speech-recognition")
            assertEquals(5,manager.packages.installed().count { it.manifest.id in upgraded && it.manifest.apiVersion==9 && it.manifest.version=="0.8.0" })
            previous.forEach { (id,enabled) -> assertEquals(enabled,manager.packages.installed().first { it.manifest.id==id }.enabled) }
        } finally { manager.close() }
    }
}
