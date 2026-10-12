package com.kigtts.next.ui.media.code

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.kigtts.next.media.HtmlProjectFiles

enum class CodeKey(val label:String, val insertion:String?=null) {
    UNDO("撤销"), REDO("重做"), LEFT("光标左移"), RIGHT("光标右移"), UP("光标上移"), DOWN("光标下移"), HOME("行首"), END("行尾"),
    TAB("插入 Tab","\t"), LEFT_PAREN("左圆括号","("), RIGHT_PAREN("右圆括号",")"), LEFT_BRACE("左花括号","{"), RIGHT_BRACE("右花括号","}"),
    LEFT_ANGLE("左尖括号","<"), RIGHT_ANGLE("右尖括号",">"), SINGLE_QUOTE("单引号","'"), DOUBLE_QUOTE("双引号","\""), SEMICOLON("分号",";")
}

/** Selection-aware commands use code points and logical lines, independent of the software keyboard. */
@Stable
class CodeEditorState(initial:String, selection:Int=0) {
    var value by mutableStateOf(TextFieldValue(initial,TextRange(selection.coerceIn(0,initial.length))));private set
    private val undo=ArrayDeque<TextFieldValue>();private val redo=ArrayDeque<TextFieldValue>()
    private var historyRevision by mutableIntStateOf(0)
    fun available(key:CodeKey):Boolean { historyRevision;return when(key) { CodeKey.UNDO->undo.isNotEmpty();CodeKey.REDO->redo.isNotEmpty();else->true } }
    fun change(next:TextFieldValue) {
        require(next.text.toByteArray(Charsets.UTF_8).size <= HtmlProjectFiles.TEXT_LIMIT) { "文件内容过长" }
        if(next.text!=value.text) {
            undo.addLast(value);while(undo.size>20 || undo.sumOf { it.text.length.toLong() }>4L*1024*1024)undo.removeFirst()
            redo.clear();historyRevision++
        }
        value=next
    }
    fun invoke(key:CodeKey) {
        if(!available(key))return
        if(key==CodeKey.UNDO) { redo.addLast(value);value=undo.removeLast();historyRevision++;return }
        if(key==CodeKey.REDO) { undo.addLast(value);value=redo.removeLast();historyRevision++;return }
        val text=value.text;val selection=value.selection
        key.insertion?.let { insert ->
            val next=text.replaceRange(selection.min,selection.max,insert)
            change(TextFieldValue(next,TextRange(selection.min+insert.length)));return
        }
        val cursor=selection.end.coerceIn(0,text.length)
        val start=if(cursor==0)0 else text.lastIndexOf('\n',cursor-1)+1
        val end=text.indexOf('\n',cursor).let { if(it<0)text.length else it }
        val target=when(key) {
            CodeKey.LEFT->if(!selection.collapsed)selection.min else if(cursor>0)text.offsetByCodePoints(cursor,-1) else 0
            CodeKey.RIGHT->if(!selection.collapsed)selection.max else if(cursor<text.length)text.offsetByCodePoints(cursor,1) else cursor
            CodeKey.HOME->start
            CodeKey.END->end
            CodeKey.UP->if(start==0)cursor else {
                val previous=if(start<=1)0 else text.lastIndexOf('\n',start-2)+1
                column(text,previous,start-1,text.codePointCount(start,cursor))
            }
            CodeKey.DOWN->if(end==text.length)cursor else {
                val following=end+1;val nextEnd=text.indexOf('\n',following).let { if(it<0)text.length else it }
                column(text,following,nextEnd,text.codePointCount(start,cursor))
            }
            else->cursor
        }
        value=TextFieldValue(text,TextRange(target))
    }
    private fun column(text:String,start:Int,end:Int,column:Int)=text.offsetByCodePoints(start,minOf(column,text.codePointCount(start,end)))
}
