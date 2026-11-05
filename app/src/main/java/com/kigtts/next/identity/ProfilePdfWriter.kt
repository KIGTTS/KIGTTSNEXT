package com.kigtts.next.identity

import android.graphics.RectF
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.US_ASCII

internal data class ProfilePdfLink(val bounds: RectF, val page: String? = null, val url: String? = null)
internal data class ProfilePdfPage(val id: String, val jpeg: ByteArray, val links: List<ProfilePdfLink>)

/** Raster content retains Android's exact fonts and shapes; PDF link annotations remain interactive. */
internal object ProfilePdfWriter {
    fun write(file: File, pages: List<ProfilePdfPage>) {
        require(pages.isNotEmpty() && pages.map { it.id }.distinct().size == pages.size)
        val bytes = ByteArrayOutputStream()
        fun text(value: String) { bytes.write(value.toByteArray(US_ASCII)) }
        val offsets = mutableListOf(0)
        fun obj(number: Int, body: () -> Unit) {
            check(number == offsets.size); offsets += bytes.size(); text("$number 0 obj\n"); body(); text("\nendobj\n")
        }
        text("%PDF-1.4\n")
        obj(1) { text("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) { text("<< /Type /Pages /Count ${pages.size} /Kids [${pages.indices.joinToString(" ") { "${3 + it * 3} 0 R" }}] >>") }
        pages.forEachIndexed { index, page ->
            val n = 3 + index * 3
            obj(n) {
                text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Image ${n + 1} 0 R >> >> /Contents ${n + 2} 0 R /Annots [")
                page.links.forEach { link ->
                    val rect = link.bounds
                    val target = link.page?.let { id -> pages.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                    val uri = link.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") || it.startsWith("mailto:") }
                    if (target != null || uri != null) {
                        text("<< /Type /Annot /Subtype /Link /Border [0 0 0] /Rect [${rect.left} ${842 - rect.bottom} ${rect.right} ${842 - rect.top}] /A << ")
                        if (target != null) text("/S /GoTo /D [${3 + target * 3} 0 R /Fit]")
                        else text("/S /URI /URI <${uri!!.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 255) }}>")
                        text(" >> >> ")
                    }
                }
                text("] >>")
            }
            obj(n + 1) {
                text("<< /Type /XObject /Subtype /Image /Width 1190 /Height 1684 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n")
                bytes.write(page.jpeg); text("\nendstream")
            }
            obj(n + 2) {
                val stream = "q 595 0 0 842 0 0 cm /Image Do Q\n"
                text("<< /Length ${stream.length} >>\nstream\n${stream}endstream")
            }
        }
        val xref = bytes.size()
        text("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
        offsets.drop(1).forEach { text("%010d 00000 n \n".format(java.util.Locale.ROOT, it)) }
        text("trailer << /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        file.parentFile!!.mkdirs(); file.outputStream().use { bytes.writeTo(it) }
    }
}
