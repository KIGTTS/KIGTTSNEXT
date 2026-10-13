package com.kigtts.next.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A save is owned by its editor token, never by whichever child happens to be foreground. */
internal class EditorSaveCompletion {
    private val pendingState=MutableStateFlow(false);val pending=pendingState.asStateFlow()
    private val completedState=MutableStateFlow(false);val completed=completedState.asStateFlow()
    var token:String?=null;private set
    var animated:Boolean=false;private set
    fun begin(owner:String):Boolean {
        if(pendingState.value)return false
        token=owner;animated=false;completedState.value=false;pendingState.value=true;return true
    }
    fun acknowledge(owner:String,animate:Boolean):Boolean {
        if(token!=owner||!pendingState.value)return false
        animated=animate;completedState.value=true;return true
    }
    fun clear(owner:String):Boolean {
        if(token!=owner)return false
        token=null;animated=false;completedState.value=false;pendingState.value=false;return true
    }
    fun ready(owner:String)=token==owner&&completedState.value
}
