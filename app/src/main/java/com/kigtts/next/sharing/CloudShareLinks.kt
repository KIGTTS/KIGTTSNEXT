package com.kigtts.next.sharing

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import java.net.URI

internal object CloudShareLinks {
    fun parse(raw: String): String? = runCatching {
        require(raw.length <= 1024)
        val wrapped = Uri.parse(raw)
        val value = if (wrapped.scheme == "kigtts" && wrapped.host == "share") {
            require(wrapped.queryParameterNames == setOf("url") && wrapped.getQueryParameters("url").size == 1)
            requireNotNull(wrapped.getQueryParameter("url"))
        } else raw
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host in setOf("share.kigtts.lhtstudio.com", "app.kigtts.lhtstudio.com") && uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null)
        require(uri.path.matches(Regex("/kigtts/(share|profile)/[A-Za-z0-9_-]{22}")))
        require(uri.rawFragment?.matches(Regex("[A-Za-z0-9_-]{43}")) == true)
        value
    }.getOrNull()
    fun from(intent: Intent?): String? {
        if (intent?.action !in setOf(Intent.ACTION_VIEW, NfcAdapter.ACTION_NDEF_DISCOVERED)) return null
        parse(intent?.dataString.orEmpty())?.let { return it }
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null
        @Suppress("DEPRECATION") val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES).orEmpty()
        return messages.filterIsInstance<NdefMessage>().asSequence().flatMap { it.records.asSequence() }
            .mapNotNull { it.toUri()?.toString()?.let(::parse) }.firstOrNull()
    }
}
