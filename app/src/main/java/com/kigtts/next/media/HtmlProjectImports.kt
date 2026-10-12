package com.kigtts.next.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/** Import a complete batch in staging first; a failed picker import leaves the project intact. */
class HtmlProjectImports(private val context: Context, private val repository: MediaRepository) {
    fun documents(id: String, directory: String, uris: List<Uri>): Int = staged(id,directory) { stage,budget ->
        uris.forEach { uri ->
            val name = if(uri.scheme=="file") File(requireNotNull(uri.path)).name else context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst())it.getString(0) else null
            }?.takeIf { it.isNotBlank() } ?: "文件"
            copy(uri,unique(stage,name),budget)
        }
    }
    fun tree(id: String, directory: String, uri: Uri): Int = staged(id,directory) { stage,budget ->
        fun visit(document: String, parent: File, depth: Int) {
            require(depth <= 16) { "目录层级过深" }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri,document)
            context.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { cursor ->
                while(cursor.moveToNext()) {
                    val child = cursor.getString(0); val name = cursor.getString(1)
                    val output = unique(parent,name)
                    if(cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        budget.item();require(output.mkdir());visit(child,output,depth+1)
                    } else copy(DocumentsContract.buildDocumentUriUsingTree(uri,child),output,budget)
                }
            } ?: error("无法读取文件夹")
        }
        val rootId=DocumentsContract.getTreeDocumentId(uri)
        val name=context.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(uri,rootId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst())it.getString(0) else null }
            ?.takeIf { it.isNotBlank() } ?: "导入的文件夹"
        val imported=unique(stage,name);budget.item();require(imported.mkdir())
        visit(rootId,imported,0)
    }
    private fun staged(id: String, directory: String, import: (File,Budget)->Unit): Int {
        val files = repository.htmlFiles;require(files.file(id,directory).isDirectory)
        val stage = File(files.root(id),".kigtts-import-${UUID.randomUUID()}");require(stage.mkdir())
        val installed = mutableListOf<File>(); val budget = Budget()
        try {
            import(stage,budget)
            stage.listFiles().orEmpty().forEach { f ->
                val output = files.file(id,files.unique(id,directory,f.name))
                java.nio.file.Files.move(f.toPath(),output.toPath());installed += output
            }
            files.touch(id);return budget.count
        } catch(e:Exception) { installed.forEach { it.deleteRecursively() };throw e }
        finally { stage.deleteRecursively() }
    }
    private fun copy(uri: Uri, output: File, budget: Budget) {
        budget.item()
        requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
            output.outputStream().use { out ->
                val buffer = ByteArray(64*1024)
                while(true) { val n = input.read(buffer);if(n<0)break;budget.bytes += n
                    require(budget.bytes <= 2L*1024*1024*1024) { "导入资源过大" };out.write(buffer,0,n) }
            }
        }
    }
    private fun unique(parent: File, name: String): File {
        require(HtmlProjectFiles.validName(name)) { "文件名称不受支持" }
        var f = File(parent,name);var index = 2
        while(f.exists())f = File(parent,name.substringBeforeLast('.',name)+" (${index++})"+name.substringAfterLast('.',"").let { if(it.isEmpty())"" else ".$it" })
        return f
    }
    private class Budget { var count = 0;var bytes = 0L;fun item() { require(++count<=2048) { "一次最多导入 2048 个文件" } } }
}
