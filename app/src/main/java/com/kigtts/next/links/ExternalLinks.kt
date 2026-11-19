package com.kigtts.next.links

import android.content.Intent
import com.kigtts.next.state.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One ordered handoff queue, including links received during another confirmation. */
class ExternalLinks(private val model: AppViewModel) {
    private val queue = ArrayDeque<ExternalLink>()
    private val mutable = MutableStateFlow<ExternalLink?>(null)
    val pending = mutable.asStateFlow()
    fun accept(intent: Intent?) {
        try { ExternalLinkParser.from(intent)?.let(::offer) }
        catch (e: Exception) { model.message(e.message ?: "无法打开这个链接") }
    }
    fun offer(link: ExternalLink): Boolean {
        link.url?.let(TrustedWebsite::validate)
        require(link.text.length <= 20000 && (link.url != null || link.text.isNotBlank()))
        if (link in queue) return true
        if (queue.size >= 16) { model.message(model.hostCopy("link.queue_full")); return false }
        queue.addLast(link)
        if (mutable.value == null) mutable.value = queue.first()
        return true
    }
    fun dismiss() { if (queue.isNotEmpty()) queue.removeFirst(); mutable.value = queue.firstOrNull() }
    fun perform(link: ExternalLink) {
        if (link != mutable.value) return
        val title = link.title.ifBlank { link.url?.let { java.net.URI(it).host } ?: link.text.take(32) }
        val category = model.state.value.selectedCategory.takeIf { it in model.categories.value } ?: model.categories.value.first()
        if (link.url == null) {
            if (link.add && model.phrases.value.none { it.mediaId == null && it.text == link.text && it.title == title }) model.quickTexts.add(link.text, category, title)
            model.navigate(Destination.Home); model.text(link.text)
        } else if (link.mode == WebOpenMode.QUICK_TEXT || link.add) {
            val existing = model.mediaRepository.entries.value.firstOrNull { it.kind == com.kigtts.next.media.MediaKind.WEB && it.primary == link.url && it.pluginId == null }
            val media = existing ?: model.mediaRepository.web(title, link.url)
            if (link.add && model.phrases.value.none { it.mediaId == media.id && it.metadata["web.openMode"] == link.mode.wire }) {
                model.quickTexts.savePhrase(QuickPhrase(link.url, category, title = title, mediaId = media.id,
                    metadata = mapOf("web.openMode" to link.mode.wire)))
            }
            if (link.mode == WebOpenMode.QUICK_TEXT) { model.navigate(Destination.Home); model.content.show(media) }
        }
    }
}
