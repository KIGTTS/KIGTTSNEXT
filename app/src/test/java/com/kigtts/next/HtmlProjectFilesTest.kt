package com.kigtts.next

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.plugin.api.SubtitleContent
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class HtmlProjectFilesTest {
    private lateinit var app:Application;private lateinit var repository:MediaRepository
    @Before fun setup() { app=ApplicationProvider.getApplicationContext();repository=MediaRepository(app,PluginPaths(app));repository.entries.value.forEach { repository.remove(it.id) } }
    @After fun cleanup() { repository.entries.value.forEach { repository.remove(it.id) } }
    @Test fun nestedMainAndRelativeAssetsSurviveRenameMoveAndRestart() {
        val entry=repository.html("项目","<p>旧页面</p>");val fs=repository.htmlFiles
        fs.create(entry.id,"","pages",true);fs.create(entry.id,"pages","home.html",false)
        fs.write(entry.id,"pages/home.html","<img src='../assets/photo.png'>")
        fs.create(entry.id,"","assets",true);fs.create(entry.id,"assets","photo.png",false)
        fs.file(entry.id,"assets/photo.png").writeBytes(byteArrayOf(1,2,3))
        fs.selectMain(entry.id,"pages/home.html");fs.rename(entry.id,"pages","screens")
        assertEquals("screens/home.html",fs.main(entry.id))
        fs.move(entry.id,listOf("screens"),"assets",false)
        assertEquals("assets/screens/home.html",fs.main(entry.id));assertTrue(repository.get(entry.id)!!.created>entry.created)
        val restored=MediaRepository(app,PluginPaths(app));assertEquals(fs.main(entry.id),restored.htmlFiles.main(entry.id))
        assertEquals("<img src='../assets/photo.png'>",restored.file(restored.get(entry.id)!!)!!.readText())
        val loader=WebResourceLoader(File(app.cacheDir,"html-test-cache"),fs.root(entry.id),false)
        assertNotNull(loader.load(WebContentPolicy.ORIGIN+"/content/assets/photo.png","GET",false))
        assertThrows(IllegalArgumentException::class.java) { fs.delete(entry.id,listOf("assets")) }
    }
    @Test fun operationsRejectEscapesAndProtectMainButAllowCopiesAndOtherDeletes() {
        val entry=repository.html("项目","正文");val fs=repository.htmlFiles
        listOf("../secret","/absolute","a\\b","a/../b","catalog.json/../../secret").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { fs.file(entry.id,path) }
        }
        assertThrows(IllegalArgumentException::class.java) { fs.rename(entry.id,"index.html","../bad") }
        assertThrows(IllegalArgumentException::class.java) { fs.delete(entry.id,listOf("index.html")) }
        fs.create(entry.id,"","assets",true)
        assertThrows(IllegalArgumentException::class.java) { fs.move(entry.id,listOf("assets"),"assets",false) }
        fs.move(entry.id,listOf("index.html"),"",true)
        assertEquals("正文",fs.read(entry.id,"index (2).html"));fs.delete(entry.id,listOf("index (2).html"))
        assertFalse(fs.file(entry.id,"index (2).html").exists());assertEquals("正文",fs.read(entry.id,"index.html"))
    }
    @Test fun batchImportPreservesNamesHandlesDuplicatesAndRollsBackOnFailure() {
        val entry=repository.html("项目","<p>主页</p>");val directory=File(app.cacheDir,"html-import-source").apply { mkdirs() }
        val css=File(directory,"theme.css").apply { writeText("body{color:red}") }
        val image=File(directory,"image.png").apply { writeBytes(byteArrayOf(1,2,3)) }
        assertEquals(3,repository.htmlImports.documents(entry.id,"",listOf(Uri.fromFile(css),Uri.fromFile(css),Uri.fromFile(image))))
        val fs=repository.htmlFiles;assertEquals("body{color:red}",fs.read(entry.id,"theme (2).css"))
        val before=fs.list(entry.id,"").map { it.path }
        assertThrows(Exception::class.java) { repository.htmlImports.documents(entry.id,"",listOf(Uri.fromFile(css),Uri.fromFile(File(directory,"missing.css")))) }
        assertEquals(before,fs.list(entry.id,"").map { it.path });assertFalse(fs.root(entry.id).listFiles()!!.any { it.name.startsWith(".kigtts-import-") })
    }
    @Test fun pluginProjectEditingCopiesAssetsWithoutChangingOwnerFiles() {
        val original=repository.plugin("test.web",SubtitleContent.Html(html="<h1>插件</h1>"))
        val source=repository.file(original)!!;File(source.parentFile,"style.css").writeText("h1{color:blue}")
        val copied=repository.editableHtml(original.id)
        assertNull(copied.pluginId);assertEquals("h1{color:blue}",repository.htmlFiles.read(copied.id,"style.css"))
        repository.htmlFiles.write(copied.id,"index.html","<p>自己的内容</p>")
        assertEquals("<h1>插件</h1>",source.readText())
    }
    @Test fun documentTreeImportRetainsDirectoriesAndMediaReferences() {
        val authority="test.html.documents";val source=File(app.cacheDir,"html-saf-source").apply { mkdirs() }
        val provider=object:android.content.ContentProvider() {
            val content=mapOf("home" to "<img src='../assets/photo.png'>","photo" to "pixels")
            override fun onCreate()=true
            override fun getType(uri:Uri)="text/plain"
            override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor {
                val id=android.provider.DocumentsContract.getDocumentId(uri)
                if(uri.lastPathSegment!="children")return android.database.MatrixCursor(arrayOf("_display_name")).apply { addRow(arrayOf("website")) }
                return android.database.MatrixCursor(arrayOf("document_id","_display_name","mime_type")).apply {
                    when(id) {
                        "root"->{addRow(arrayOf("pages","pages","vnd.android.document/directory"));addRow(arrayOf("assets","assets","vnd.android.document/directory"))}
                        "pages"->addRow(arrayOf("home","home.html","text/html"))
                        "assets"->addRow(arrayOf("photo","photo.png","image/png"))
                    }
                }
            }
            override fun openFile(uri:Uri,mode:String):android.os.ParcelFileDescriptor {
                val id=android.provider.DocumentsContract.getDocumentId(uri)
                return android.os.ParcelFileDescriptor.open(File(source,id).apply {writeText(content.getValue(id))},android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            }
            override fun insert(uri:Uri,values:android.content.ContentValues?)=null
            override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
            override fun update(uri:Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
        }
        provider.attachInfo(app,android.content.pm.ProviderInfo().apply { this.authority=authority })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority,provider)
        val entry=repository.html("目录","<p>原始</p>")
        assertEquals(5,repository.htmlImports.tree(entry.id,"",android.provider.DocumentsContract.buildTreeDocumentUri(authority,"root")))
        assertEquals("<img src='../assets/photo.png'>",repository.htmlFiles.read(entry.id,"website/pages/home.html"))
        assertEquals("pixels",repository.htmlFiles.file(entry.id,"website/assets/photo.png").readText())
        repository.htmlFiles.selectMain(entry.id,"website/pages/home.html");assertTrue(repository.file(repository.get(entry.id)!!)!!.isFile)
        repository.htmlImports.tree(entry.id,"",android.provider.DocumentsContract.buildTreeDocumentUri(authority,"root"))
        assertTrue(repository.htmlFiles.file(entry.id,"website (2)/assets/photo.png").isFile)
        assertEquals("<img src='../assets/photo.png'>",repository.htmlFiles.read(entry.id,"website (2)/pages/home.html"))
    }
}
