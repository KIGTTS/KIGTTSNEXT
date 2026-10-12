@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.*
import com.kigtts.next.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class QuickCardsArchiveTest {
    private lateinit var model:AppViewModel;private lateinit var broker:QuickTextBroker
    @Before fun setup() {Dispatchers.setMain(UnconfinedTestDispatcher());val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("quick-phrases",0).edit().clear().commit();model=testAppModel(app)
        broker=model.quickTextPlugins.forPlugin("test.bundle",setOf("quick-text-write"))}
    @After fun finish() {Dispatchers.resetMain()}
    @Test fun htmlAndPairedMediaRoundTripWithIdRemappingAndRepeatedImportDoesNotDuplicate()=runBlocking {
        val storage=model.pluginManager.paths.storage("test.bundle")
        fun file(path:String,text:String)=storage.file(path).apply {parentFile!!.mkdirs();writeText(text)}
        file("resources/widget/pages/index.html","<img src='../assets/photo.png'>");file("resources/widget/assets/photo.png","image")
        val html=broker.request(QuickTextCommand.PublishPage(PluginResource("resources/widget"),"pages/index.html")) as QuickTextReply.Media
        file("resources/live/photo.png","photo");file("resources/live/clip.mp4","clip")
        val live=broker.request(QuickTextCommand.PublishMedia(SubtitleContent.Image(PluginResource("resources/live/photo.png"),motionVideo=PluginResource("resources/live/clip.mp4")))) as QuickTextReply.Media
        val snapshot=(broker.request(QuickTextCommand.Query()) as QuickTextReply.Snapshot).value;val group=snapshot.groups.first()
        val page=QuickEntry(groupId=group.id,text="工具",kind=QuickCardKind.PAGE,mediaId=html.value.id,metadata=mapOf("test.bundle/name" to "calculator"))
        val picture=QuickEntry(groupId=group.id,text="照片",kind=QuickCardKind.MEDIA,mediaId=live.value.id,autoPlay=false,audioPolicy=QuickAudioPolicy.SILENT)
        val prepared=broker.request(QuickTextCommand.Prepare("add",snapshot.revision,listOf(QuickTextEdit.PutEntry(page),QuickTextEdit.PutEntry(picture)))) as QuickTextReply.Prepared
        broker.request(QuickTextCommand.Commit(prepared.value.token))
        val exported=broker.request(QuickTextCommand.Export(entryIds=listOf(page.id,picture.id))) as QuickTextReply.Resource
        assertTrue(storage.file(exported.source.path).exists())
        val import=QuickTextCommand.Import(exported.source,"import",model.quickTexts.revision.value)
        val imported=broker.request(import) as QuickTextReply.Prepared;val mediaCount=model.mediaRepository.entries.value.size
        assertEquals(imported,broker.request(import));assertEquals(mediaCount,model.mediaRepository.entries.value.size)
        val result=broker.request(QuickTextCommand.Commit(imported.value.token)) as QuickTextReply.Applied
        assertEquals(result,broker.request(import))
        val mapping=org.json.JSONObject(storage.file(imported.mapping!!.path).readText())
        val pageId=mapping.getJSONObject("entries").getString(page.id);val liveId=mapping.getJSONObject("entries").getString(picture.id)
        val restored=model.phrases.value.first {it.id==pageId};assertEquals("calculator",restored.metadata["test.bundle/name"])
        assertNotEquals(page.id,pageId);assertNotEquals(group.name,restored.category)
        val entry=model.mediaRepository.get(restored.mediaId)!!;assertEquals("test.bundle",entry.pluginId)
        assertEquals("<img src='../assets/photo.png'>",model.mediaRepository.file(entry)!!.readText())
        assertEquals("image",model.pluginManager.paths.storage("test.bundle").file(entry.projectRoot!!+"/assets/photo.png").readText())
        val restoredLive=model.phrases.value.first {it.id==liveId};assertFalse(restoredLive.autoPlay)
        assertEquals("clip",model.mediaRepository.file(model.mediaRepository.get(restoredLive.mediaId)!!,true)!!.readText())
    }
    @Test fun traversalAndOversizedMetadataAreRejectedAndDoNotChangeTheLibrary()=runBlocking {
        val storage=model.pluginManager.paths.storage("test.bundle");val before=model.phrases.value
        for((name,body) in listOf("../outside" to byteArrayOf(1),"cards.json" to ByteArray(8*1024*1024+1) {32})) {
            val archive=storage.file("imports/bad.kigcards").apply {parentFile!!.mkdirs()}
            ZipOutputStream(archive.outputStream()).use {zip->zip.putNextEntry(ZipEntry(name));zip.write(body);zip.closeEntry()}
            val reply=broker.request(QuickTextCommand.Import(PluginResource("imports/bad.kigcards"),"bad",model.quickTexts.revision.value))
            assertTrue(reply is QuickTextReply.Failure);assertEquals(before,model.phrases.value)
        }
        assertFalse(storage.root.parentFile!!.resolve("outside").exists())
    }
}
