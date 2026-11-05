package com.kigtts.next.sharing

import java.io.*
import java.net.*
import java.util.concurrent.*

internal class PhotoHttpRequest(val method: String, val path: String, val headers: Map<String, String>, val input: InputStream) {
    val length = headers["content-length"]?.toLongOrNull() ?: 0
    fun body(limit: Int = 65536): ByteArray {
        require(length in 0..limit.toLong())
        val data = ByteArray(length.toInt()); DataInputStream(input).readFully(data); return data
    }
}
internal class PhotoHttpResponse(private val output: OutputStream) {
    private var sent = false
    fun header(status: Int, mime: String, length: Long? = null, extra: Map<String, String> = emptyMap()) {
        check(!sent); sent = true
        val reason = when (status) { 200 -> "OK"; 202 -> "Accepted"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 409 -> "Conflict"; else -> "Unavailable" }
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\nContent-Type: $mime\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n")
            append("Content-Security-Policy: default-src 'none'; img-src 'self' data:; font-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'\r\n")
            length?.let { append("Content-Length: $it\r\n") }
            extra.forEach { (key, value) -> require('\r' !in value && '\n' !in value); append("$key: $value\r\n") }
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.US_ASCII))
    }
    fun text(value: String, mime: String = "application/json; charset=utf-8", status: Int = 200) {
        val bytes = value.toByteArray(Charsets.UTF_8); header(status, mime, bytes.size.toLong()); output.write(bytes)
    }
    fun file(file: File, mime: String, download: Boolean) {
        header(200, mime, file.length(), if (download) mapOf("Content-Disposition" to "attachment; filename=\"${file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}\"") else emptyMap())
        file.inputStream().use { it.copyTo(output) }
    }
    fun stream(mime: String, name: String, write: (OutputStream) -> Unit) {
        header(200, mime, extra = mapOf("Content-Disposition" to "attachment; filename=\"$name\"")); write(output)
    }
}

/** Bounded HTTP/1.1 server. All files are explicitly registered; paths never map onto a directory. */
internal class LocalPhotoServer(private val handle: (PhotoHttpRequest, PhotoHttpResponse) -> Unit) : Closeable {
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(0, 6, 20, TimeUnit.SECONDS, SynchronousQueue(),
        { task -> Thread(task, "KIGTTS photo request").apply { isDaemon = true } })
    private val server = ServerSocket(0, 8)
    val port get() = server.localPort
    init { Thread({
        while (!server.isClosed) try {
            val socket = server.accept()
            if (!socket.inetAddress.isSiteLocalAddress && !socket.inetAddress.isLoopbackAddress && !socket.inetAddress.isLinkLocalAddress) { socket.close(); continue }
            sockets += socket
            try { workers.execute { serve(socket) } } catch (_: RejectedExecutionException) { sockets -= socket; socket.close() }
        } catch (_: IOException) { if (!server.isClosed) continue }
    }, "KIGTTS photo listener").apply { isDaemon = true; start() } }
    private fun serve(socket: Socket) {
        socket.use {
            try {
                socket.soTimeout = 60_000
                val input = BufferedInputStream(socket.getInputStream()); val output = BufferedOutputStream(socket.getOutputStream())
                val first = line(input, 4096).split(' '); require(first.size == 3 && first[2] == "HTTP/1.1")
                require(first[1].startsWith('/') && !first[1].contains(".."))
                val headers = linkedMapOf<String, String>(); var count = 0
                while (true) {
                    val line = line(input, 4096); count += line.length; require(count < 16384)
                    if (line.isEmpty()) break
                    val split = line.indexOf(':'); require(split > 0)
                    val name = line.take(split).lowercase(); require(name !in headers)
                    headers[name] = line.substring(split + 1).trim()
                }
                require("transfer-encoding" !in headers)
                val request = PhotoHttpRequest(first[0], first[1], headers, input)
                require(request.length in 0..268_435_456)
                val response = PhotoHttpResponse(output)
                try { handle(request, response) } catch (_: Exception) { runCatching { response.text("{}", status = 400) } }
                output.flush()
            } catch (_: Exception) { } finally { sockets -= socket }
        }
    }
    override fun close() { server.close(); sockets.forEach { runCatching { it.close() } }; sockets.clear(); workers.shutdownNow() }
    private fun line(input: InputStream, limit: Int): String {
        val result = ByteArrayOutputStream()
        while (result.size() < limit) {
            val next = input.read(); if (next < 0) throw EOFException()
            if (next == 10) return result.toString("US-ASCII").removeSuffix("\r")
            result.write(next)
        }
        throw IOException("Header too long")
    }
}
