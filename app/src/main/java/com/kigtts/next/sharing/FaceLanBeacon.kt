package com.kigtts.next.sharing

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Broadcast fallback for LANs where mDNS is filtered. Socket work never blocks the UI thread. */
internal class FaceLanBeacon(private val identity: FaceLanIdentity,
    private val destinations: () -> List<InetSocketAddress>, private val found: (FacePeer) -> Unit,
    private val lost: (String) -> Unit,
    private val openSocket: () -> DatagramSocket = { DatagramSocket(null).apply {
        reuseAddress = true; broadcast = true; bind(InetSocketAddress(FACE_LAN_PORT))
    } }) : Closeable {
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val socket = AtomicReference<DatagramSocket?>()
    private var worker: Thread? = null

    fun start() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        worker = Thread({
            while (!closed.get()) {
                try {
                    openSocket().use { active ->
                        socket.set(active)
                        if (closed.get()) return@use
                        active.soTimeout = 750
                        listen(active)
                    }
                } catch (_: Exception) { }
                finally { socket.getAndSet(null)?.close() }
                if (!closed.get()) try { Thread.sleep(2_000) } catch (_: InterruptedException) { }
            }
        }, "KIGTTS local discovery").apply { isDaemon = true; start() }
    }

    private fun listen(active: DatagramSocket) {
        val seen = mutableMapOf<String, Long>()
        val answered = linkedMapOf<String, Long>()
        val buffer = ByteArray(FACE_LAN_PACKET_LIMIT + 1)
        val query = FaceLanProtocol.query(identity.id)
        var nextQuery = 0L
        try {
            while (!closed.get()) {
                val now = System.nanoTime() / 1_000_000
                if (now >= nextQuery) {
                    nextQuery = now + 3_000
                    runCatching { destinations().take(16) }.getOrDefault(emptyList()).forEach { destination ->
                        runCatching { active.send(DatagramPacket(query, query.size, destination)) }
                    }
                }
                seen.filterValues { now - it > 12_000 }.keys.toList().forEach { seen.remove(it); lost(it) }
                val packet = DatagramPacket(buffer, buffer.size)
                try { active.receive(packet) } catch (_: SocketTimeoutException) { continue }
                val json = FaceLanProtocol.parse(buffer, packet.length, packet.address) ?: continue
                val id = json.optString("id")
                if (id == identity.id) continue
                if (json.optString("kind") == "query") {
                    val host = packet.address.hostAddress.orEmpty()
                    if (answered[host]?.let { now - it < 750 } == true) continue
                    if (answered.size >= 64) answered.remove(answered.keys.first())
                    answered[host] = now
                    val reply = FaceLanProtocol.response(identity, id)
                    runCatching { active.send(DatagramPacket(reply, reply.size, packet.socketAddress)) }
                } else FaceLanProtocol.peer(json, packet.address, identity.id)?.let { peer ->
                    if (seen.size < 64 || peer.id in seen) {
                        seen[peer.id] = now
                        // Refresh also updates an endpoint/token changed by a new sharing session.
                        if (!closed.get()) found(peer)
                    }
                }
            }
        } finally { if (!closed.get()) seen.keys.forEach(lost) }
    }

    override fun close() { closed.set(true); socket.getAndSet(null)?.close(); worker?.interrupt() }
}
