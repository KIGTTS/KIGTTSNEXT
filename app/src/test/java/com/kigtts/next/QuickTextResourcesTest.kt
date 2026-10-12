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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class QuickTextResourcesTest {
    private lateinit var model:AppViewModel;private lateinit var broker:QuickTextBroker
    @Before fun setup() {Dispatchers.setMain(UnconfinedTestDispatcher());val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("quick-phrases",0).edit().clear().commit();model=testAppModel(app);broker=model.quickTextPlugins.forPlugin("test.tools",setOf("quick-text-write"))}
    @After fun end() {Dispatchers.resetMain()}
    private fun resource(path:String,text:String)=PluginResource(path).also {model.pluginManager.paths.storage("test.tools").file(path).apply {parentFile!!.mkdirs();writeText(text)}}
    private suspend fun page():QuickMedia {
        resource("resources/calc/pages/index.html","<script src='../assets/tool.js'></script>");resource("resources/calc/assets/tool.js","const value=2+2;")
        return (broker.request(QuickTextCommand.PublishPage(PluginResource("resources/calc"),"pages/index.html")) as QuickTextReply.Media).value
    }
    @Test fun generatedNestedPagesAndCopiesStayInTheCallingPluginsData()=runBlocking {
        val page=page();val entry=model.mediaRepository.get(page.id)!!
        assertEquals("test.tools",entry.pluginId);assertEquals("resources/calc",entry.projectRoot)
        val list=broker.request(QuickTextCommand.Files(page.id,"assets")) as QuickTextReply.Files;assertEquals("assets/tool.js",list.items.single().path)
        val output=broker.request(QuickTextCommand.GetFile(page.id,"assets/tool.js","exports/tool.js")) as QuickTextReply.Resource
        assertEquals("const value=2+2;",model.pluginManager.paths.storage("test.tools").file(output.source.path).readText())
        val other=model.quickTextPlugins.forPlugin("test.other",setOf("quick-text-write"))
        val input=model.pluginManager.paths.storage("test.other").file("resources/input.js").apply {parentFile!!.mkdirs();writeText("other")}
        assertEquals(QuickTextError.PERMISSION_DENIED,(other.request(QuickTextCommand.PutFile(page.id,"assets/tool.js",PluginResource(input.relativeTo(model.pluginManager.paths.storage("test.other").root).invariantSeparatorsPath),page.version)) as QuickTextReply.Failure).code)
        assertEquals(QuickTextError.INVALID,(broker.request(QuickTextCommand.GetFile(page.id,"../escape","exports/bad")) as QuickTextReply.Failure).code)
    }
    @Test fun fileBatchRollsBackAndUpdatingMainIsVersioned()=runBlocking {
        val page=page();val source=resource("resources/input.html","<p>新页面</p>")
        val bad=broker.request(QuickTextCommand.EditFiles(page.id,page.version,listOf(QuickFileEdit.Put("assets/new.html",source),QuickFileEdit.Remove(listOf("pages/index.html")))))
        assertTrue(bad is QuickTextReply.Failure);assertFalse(model.pluginManager.paths.storage("test.tools").file("resources/calc/assets/new.html").exists())
        val updated=broker.request(QuickTextCommand.EditFiles(page.id,page.version,listOf(QuickFileEdit.Put("assets/new.html",source),QuickFileEdit.Main("assets/new.html"),QuickFileEdit.Remove(listOf("pages/index.html"))))) as QuickTextReply.Media
        assertTrue(updated.value.version>page.version);assertEquals("assets/new.html",updated.value.mainPage)
        assertEquals(QuickTextError.CONFLICT,(broker.request(QuickTextCommand.SetMain(page.id,"assets/new.html",page.version)) as QuickTextReply.Failure).code)
        assertEquals("<p>新页面</p>",model.mediaRepository.file(model.mediaRepository.get(page.id)!!)!!.readText())
    }
}
