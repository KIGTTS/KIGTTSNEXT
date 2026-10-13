package com.kigtts.next.ui.media

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kigtts.next.media.*
import com.kigtts.next.ui.components.*

internal sealed interface HtmlFileCommand {
    data class Create(val folder:Boolean):HtmlFileCommand
    data class Rename(val item:HtmlProjectItem):HtmlFileCommand
    data class Delete(val paths:List<String>):HtmlFileCommand
}
internal data class HtmlFileTransfer(val paths: List<String>, val copy: Boolean)

@Composable
internal fun HtmlFileDialog(action:HtmlFileCommand,busy:Boolean,error:String?,close:()->Unit,confirm:(String,()->Unit)->Unit) {
    var name by remember(action) { mutableStateOf(when(action) { is HtmlFileCommand.Rename->action.item.name;is HtmlFileCommand.Create->if(action.folder)"" else "page.html";else->"" }) }
    MotionAlertDialog(onDismissRequest=close,dismissEnabled=!busy,title={Text(when(action) { is HtmlFileCommand.Create->if(action.folder)"新建文件夹" else "新建文件";is HtmlFileCommand.Rename->"重命名";is HtmlFileCommand.Delete->"删除所选文件？" })},
        text={ Column { if(action is HtmlFileCommand.Delete)Text("删除后无法恢复。主页面需要先替换后才能删除。")
            else OutlinedTextField(name,{name=it},label={Text("名称")},singleLine=true,enabled=!busy)
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)} } },
        confirmButton={ val finish=rememberDialogClose();MotionTextButton(feedbackClick { confirm(name) { finish(close) } },
            enabled=!busy&&(action is HtmlFileCommand.Delete || HtmlProjectFiles.validName(name))) { Text(if(action is HtmlFileCommand.Delete)"删除" else "确定") } },
        dismissButton={ MotionTextButton(dialogFeedbackClick(close),enabled=!busy) { Text("取消") } })
}

@Composable
internal fun HtmlFilePreview(item:HtmlProjectItem,repository:MediaRepository,id:String,close:()->Unit) {
    val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.name.substringAfterLast('.').lowercase()).orEmpty()
    val kind=when { mime.startsWith("image/")->MediaKind.IMAGE;mime.startsWith("video/")->MediaKind.VIDEO;mime.startsWith("audio/")->MediaKind.AUDIO;else->null }
    MotionAlertDialog(onDismissRequest=close,title={Text(item.name)},text={
        if(kind==null)Text("此文件已保存，可在页面中引用。") else com.kigtts.next.ui.home.PhraseResourcePreview(
            MediaEntry("$id/${item.path}",item.name,kind,repository.htmlFiles.root(id).name+"/"+item.path,mime),repository,true,false,null,false)
    },confirmButton={ MotionTextButton(dialogFeedbackClick(close)) { Text("关闭") } })
}
