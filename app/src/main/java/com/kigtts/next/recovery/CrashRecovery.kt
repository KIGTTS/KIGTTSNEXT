package com.kigtts.next.recovery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** Only actual uncaught exceptions count; rotation, normal process eviction and force-stop do not. */
class CrashRecovery(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val root = File(context.filesDir, "recovery").apply { mkdirs() }
    private val file = File(root, "state.json")
    private val started = now()
    fun safeMode(): Boolean = locked { it.optBoolean("safeMode") }
    fun record(error: Throwable, mainProcess: Boolean) = locked { state ->
        val time = now()
        val crashes = state.optJSONArray("crashes") ?: JSONArray()
        val recent = (0 until crashes.length()).map { crashes.optLong(it) }.filter { time - it in 0..600_000 }.toMutableList()
        if (!mainProcess || time - started in 0..45_000) recent += time
        state.put("crashes", JSONArray(recent.takeLast(12)))
        if (recent.size >= 3) state.put("safeMode", true)
        state.put("lastCrash", time).put("process", if (mainProcess) "app" else "plugin")
        state.put("exception", error.javaClass.name).put("stack", error.stackTrace.take(80).joinToString("\n").take(16000))
        write(state)
    }
    fun restore() = locked { state -> state.put("safeMode", false).put("crashes", JSONArray()); write(state) }
    fun report(): String = locked { it.toString(2) }
    private fun write(state: JSONObject) {
        val stage = File.createTempFile("state-", ".pending", root)
        try {
            java.io.FileOutputStream(stage).use { stream -> stream.write(state.toString().toByteArray(Charsets.UTF_8)); stream.fd.sync() }
            java.nio.file.Files.move(stage.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } finally { stage.delete() }
    }
    private fun <T> locked(action: (JSONObject) -> T): T = synchronized(processLock) {
        RandomAccessFile(File(root, "state.lock"), "rw").use { handle -> handle.channel.lock().use {
            val value = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
            action(value)
        } }
    }
    companion object { private val processLock = Any() }
}
