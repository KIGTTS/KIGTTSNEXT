package com.kigtts.next.media

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object WebAndroidPermissions {
    data class Request(val permissions: List<String>, val result: CompletableDeferred<Boolean> = CompletableDeferred())
    val pending = MutableStateFlow<Request?>(null)
    private val gate = Mutex()
    suspend fun request(context: Context, permissions: List<String>): Boolean = gate.withLock {
        val needed = permissions.filter { ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) return@withLock true
        val value = Request(needed); pending.value = value
        try { withTimeoutOrNull(90000) { value.result.await() } == true }
        finally { if (pending.value === value) pending.value = null }
    }
    fun finish(grants: Map<String, Boolean>) { pending.value?.let { it.result.complete(it.permissions.all { permission -> grants[permission] == true }) } }
    fun cancel() { pending.value?.result?.complete(false); pending.value = null }
}
