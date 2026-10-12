package com.kigtts.next.ui.media

/** A layout or orientation change must not reset manual pause or replay a live photo. */
class MediaPlaybackSessions {
    private val sessions = linkedMapOf<Pair<String,Int>,LivePhotoState>()
    fun get(id:String,revision:Int):LivePhotoState = sessions.getOrPut(id to revision) {
        if(sessions.size>=2)sessions.remove(sessions.keys.first())
        LivePhotoState()
    }
}
