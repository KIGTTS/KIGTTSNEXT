package com.kigtts.plugin.runtime

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class PermissionAccess { ASK, ALLOW, DENY }
data class PermissionPrompt(val token: String, val owner: String, val permission: String,
    val name: String, val purpose: String, val detail: String = "")

/** Host-owned grants. Once belongs to exactly one suspended operation, never to a whole process. */
class PluginPermissions(context: Context) {
    private val preferences = context.getSharedPreferences("plugin-permissions", Context.MODE_PRIVATE)
    private val lock = Any()
    private data class Waiting(val prompt: PermissionPrompt, val result: CompletableDeferred<Boolean>)
    private val waiting = linkedMapOf<String, Waiting>()
    private val prompts = MutableStateFlow<List<PermissionPrompt>>(emptyList())
    val pending = prompts.asStateFlow()
    @Volatile var foreground = false
    fun access(owner: String, permission: String): PermissionAccess = synchronized(lock) {
        runCatching { PermissionAccess.valueOf(preferences.getString(key(owner, permission), "ASK")!!) }.getOrDefault(PermissionAccess.ASK)
    }
    fun set(owner: String, permission: String, access: PermissionAccess) = synchronized(lock) {
        preferences.edit().putString(key(owner, permission), access.name).apply()
        val found = waiting.values.filter { it.prompt.owner == owner && it.prompt.permission == permission }
        found.forEach { waiting.remove(it.prompt.token); it.result.complete(access == PermissionAccess.ALLOW) }
        publish()
    }
    suspend fun request(owner: String, permission: String, name: String, purpose: String, detail: String = ""): Boolean {
        val item = synchronized(lock) {
            when (access(owner, permission)) {
                PermissionAccess.ALLOW -> return true
                PermissionAccess.DENY -> return false
                PermissionAccess.ASK -> Unit
            }
            if (!foreground) return false
            Waiting(PermissionPrompt(UUID.randomUUID().toString(), owner, permission, name, purpose.take(600), detail.take(300)), CompletableDeferred()).also {
                waiting[it.prompt.token] = it; publish()
            }
        }
        try { return withTimeoutOrNull(120_000) { item.result.await() } == true }
        finally { synchronized(lock) { waiting.remove(item.prompt.token); publish() } }
    }
    /** null dismisses; ASK permits this call only, ALLOW/DENY persist. */
    fun decide(token: String, decision: PermissionAccess?) = synchronized(lock) {
        val item = waiting.remove(token) ?: return@synchronized
        if (decision == PermissionAccess.ALLOW || decision == PermissionAccess.DENY) {
            preferences.edit().putString(key(item.prompt.owner, item.prompt.permission), decision.name).apply()
            waiting.values.filter { it.prompt.owner == item.prompt.owner && it.prompt.permission == item.prompt.permission }.forEach {
                waiting.remove(it.prompt.token); it.result.complete(decision == PermissionAccess.ALLOW)
            }
        }
        item.result.complete(decision == PermissionAccess.ALLOW || decision == PermissionAccess.ASK); publish()
    }
    fun clear(owner: String) = synchronized(lock) {
        preferences.edit().also { edit -> preferences.all.keys.filter { it.startsWith("$owner|") }.forEach(edit::remove) }.apply()
        waiting.values.filter { it.prompt.owner == owner }.forEach { waiting.remove(it.prompt.token); it.result.complete(false) }; publish()
    }
    fun clearPrefix(prefix: String) = synchronized(lock) {
        require(prefix.startsWith("web-device:") && prefix.endsWith(':'))
        preferences.edit().also { edit -> preferences.all.keys.filter { it.startsWith(prefix) }.forEach(edit::remove) }.apply()
        waiting.values.filter { it.prompt.owner.startsWith(prefix) }.forEach { waiting.remove(it.prompt.token); it.result.complete(false) }; publish()
    }
    fun background() = synchronized(lock) {
        foreground = false; waiting.values.forEach { it.result.complete(false) }; waiting.clear(); publish()
    }
    private fun publish() { prompts.value = waiting.values.map { it.prompt } }
    private fun key(owner: String, permission: String): String {
        require(owner.length in 1..220 && !owner.contains('|') && permission.matches(Regex("[a-z][a-z-]{0,63}")))
        return "$owner|$permission"
    }
    companion object {
        val sensitive = setOf("network", "notifications", "app-control", "quick-text-read", "quick-text-write", "stickers-read", "stickers-write", "tts", "asr-batch", "asr-segmented", "asr-incremental", "speech-service", "asr-service", "voice-packs")
        fun label(permission: String) = when (permission) {
            "network" -> "访问网络"; "app-control" -> "操作应用"; "quick-text-read" -> "读取快捷文本"
            "notifications" -> "发送通知与实况进度"
            "quick-text-write" -> "修改快捷文本"; "stickers-read" -> "读取拍立得与贴纸"; "stickers-write" -> "管理拍立得与贴纸"
            "tts", "speech-service" -> "合成与播放语音"; "asr-batch", "asr-segmented", "asr-incremental", "asr-service" -> "处理语音识别音频"
            "voice-packs" -> "管理语音包"
            "camera" -> "使用摄像头"; "microphone" -> "使用麦克风"; "motion-sensors" -> "读取运动与重力传感器"; "downloads" -> "下载文件"
            else -> permission
        }
    }
}
