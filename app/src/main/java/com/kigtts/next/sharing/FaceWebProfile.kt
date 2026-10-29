package com.kigtts.next.sharing

import android.annotation.SuppressLint
import android.content.Context
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kigtts.next.identity.*
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

internal fun faceHtml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** Only bundled Remix vectors reach HTML; received profile data cannot inject markup. */
internal class FaceWebIcons(private val context: Context) {
    private val cached = ConcurrentHashMap<SocialPlatform, String>()
    // Resources.getXml also reads compiled vector drawables; only bundled vectors are used here.
    @SuppressLint("ResourceType")
    fun icon(link: ContactLink): String = cached.getOrPut(socialPlatform(link)) {
        context.resources.getXml(socialPlatform(link).icon).use { xml ->
            val paths = mutableListOf<String>()
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path")
                    xml.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData")?.let { paths += "<path d=\"${faceHtml(it)}\"/>" }
                xml.next()
            }
            "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\">${paths.joinToString("")}</svg>"
        }
    }
}

internal fun faceContactUrl(link: ContactLink): String? {
    val value = contactPayload(link)
    if (value.any { it.isISOControl() }) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf { when (uri.scheme?.lowercase()) {
        "https", "http" -> !uri.host.isNullOrBlank() && uri.userInfo == null
        "mailto", "tel" -> uri.rawSchemeSpecificPart.isNotBlank()
        else -> false
    } }
}

private fun contactQr(value: String): String = runCatching {
    val bits = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 1, 1,
        mapOf(EncodeHintType.MARGIN to 3, EncodeHintType.CHARACTER_SET to "UTF-8"))
    val path = buildString { for (y in 0 until bits.height) for (x in 0 until bits.width) if (bits[x, y]) append("M$x $y h1v1h-1z") }
    "<svg class=\"contact-qr\" viewBox=\"0 0 ${bits.width} ${bits.height}\" role=\"img\" aria-label=\"联系方式二维码\"><path d=\"$path\"/></svg>"
}.getOrDefault("<p>内容较长，请复制下方文字。</p>")

internal fun faceContactDialog(link: ContactLink, id: String): String {
    val label = faceHtml(link.label.ifBlank { "联系方式" })
    val url = faceContactUrl(link)
    return """<dialog class="contact-dialog" id="$id"><button class="contact-close" data-close="$id" aria-label="关闭">×</button><h2>$label</h2>${contactQr(contactPayload(link))}<p class="contact-value">${faceHtml(link.value)}</p><div class="contact-actions"><button data-copy="$id">复制</button>${url?.let { "<a href=\"${faceHtml(it)}\" target=\"_blank\" rel=\"noopener noreferrer\">打开链接 ↗</a>" }.orEmpty()}</div><span class="copy-status" role="status"></span></dialog>"""
}

internal fun faceProfileMarkup(json: JSONObject, cover: FaceAsset?, path: String, icon: (ContactLink) -> String, media: List<FaceAsset> = emptyList()): String {
    val person = PublicIdentityProfile.from(json)
    val avatar = person.avatar?.takeIf { it.matches(Regex("[A-Za-z0-9+/=]{1,65535}")) }?.let {
        val clip = AvatarFrames.svgPath(person.avatarFrame)
        """<svg class="avatar" viewBox="0 0 1 1" role="img" aria-label="头像"><defs><clipPath id="avatar-clip" clipPathUnits="objectBoundingBox"><path d="$clip"/></clipPath></defs><image href="data:image/jpeg;base64,$it" width="1" height="1" preserveAspectRatio="xMidYMid slice" clip-path="url(#avatar-clip)"/></svg>"""
    } ?: "<div class=\"avatar avatar-fallback\" aria-hidden=\"true\">${faceHtml(person.name.take(1))}</div>"
    val contacts = person.contacts.mapIndexed { i, link ->
        val label = faceHtml(link.label.ifBlank { "联系方式" })
        """<button class="contact-link" data-contact="contact-$i">${icon(link)}<span class="contact-label"><strong>$label</strong><small>${faceHtml(link.value)}</small></span><span aria-hidden="true">↗</span></button>""" + faceContactDialog(link, "contact-$i")
    }.joinToString("")
    return """<header class="profile"><div class="brand">KIGTTS · 面对面</div>${cover?.let { "<img class=\"profile-cover\" src=\"${path}file/${it.id}\" alt=\"个人名片\">" }.orEmpty()}$avatar<h1>${faceHtml(person.name)}</h1>${person.bio.takeIf(String::isNotBlank)?.let { "<p class=\"bio\">${faceHtml(it)}</p>" }.orEmpty()}${faceWebTiles(person, path, media, icon)}${if (contacts.isNotBlank()) "<section class=\"profile-group\"><h2>联系方式</h2><nav class=\"contact-links\" aria-label=\"联系方式\">$contacts</nav></section>" else ""}</header>"""
}

internal const val faceProfileStyles = """
.profile{max-width:600px;margin:auto;padding:32px 24px 12px;text-align:center}.brand{font-size:12px;letter-spacing:.12em;color:#577274;margin-bottom:28px}.avatar{width:88px;height:88px;margin:0 auto 12px;display:block;flex:none}.avatar-fallback{border-radius:32px;background:#d7eae6;display:grid;place-items:center;font-size:36px}.profile-cover{aspect-ratio:16/9;object-fit:cover;width:100%;border-radius:24px;display:block;margin-bottom:24px}.bio{white-space:pre-wrap;overflow-wrap:anywhere;color:#526b6d;margin:12px auto 24px}.contact-links{display:grid;gap:12px;margin:28px 0}.contact-link{border:1px solid #dce8e5;border-radius:22px;background:#ffffffd9;color:inherit;display:flex;align-items:center;gap:16px;width:100%;padding:18px 20px;text-align:left;transition:transform .18s,background .18s}.contact-link:hover{background:white;transform:translateY(-2px)}.contact-link svg{width:24px;height:24px;fill:currentColor;flex:none}.contact-link .contact-label{flex:1;min-width:0}.contact-link small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin-top:5px;color:#526b6d}.contact-dialog{inset:0;margin:auto;width:min(420px,calc(100vw - 32px));height:fit-content;max-height:90dvh;overflow:auto;background:#f6faf8;color:#172e30;border-radius:28px;padding:32px 24px;text-align:center}.contact-dialog[open]{display:block}.contact-dialog::backdrop{background:#0008;backdrop-filter:blur(6px)}.contact-close{position:absolute;right:12px;top:12px;background:none;border:0;color:inherit;font-size:28px;width:40px;height:40px}.contact-qr{display:block;width:min(100%,260px);margin:20px auto;background:white;fill:#172e30}.contact-value{overflow-wrap:anywhere;white-space:pre-wrap}.contact-actions{display:flex;gap:8px;justify-content:center}.contact-actions a,.contact-actions button,.card-download a{border:0;background:#d5e8e6;color:#164d50;border-radius:28px;text-decoration:none;padding:14px 24px;display:inline-block}.copy-status{display:block;min-height:24px;font-size:13px;margin-top:8px}.card-download{text-align:center;margin:0 auto 32px}.page-note{text-align:center;color:#577274;font-size:13px;margin:24px}button:focus-visible,a:focus-visible{outline:3px solid #338084;outline-offset:3px}@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
"""

internal const val faceProfileScript = """
document.querySelectorAll('[data-contact]').forEach(b=>b.onclick=()=>document.getElementById(b.dataset.contact).showModal());document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>document.getElementById(b.dataset.close).close());document.querySelectorAll('[data-copy]').forEach(b=>b.onclick=async()=>{const d=document.getElementById(b.dataset.copy),v=d.querySelector('.contact-value').textContent;try{if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(v);else{const t=document.createElement('textarea');t.value=v;t.style.position='fixed';t.style.opacity='0';document.body.append(t);t.select();const ok=document.execCommand('copy');t.remove();if(!ok)throw Error();}d.querySelector('.copy-status').textContent='已复制';}catch{d.querySelector('.copy-status').textContent='请长按上方文字复制';}});
"""
