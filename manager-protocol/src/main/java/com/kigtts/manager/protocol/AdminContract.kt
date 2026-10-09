package com.kigtts.manager.protocol

object AdminContract {
    const val VERSION = 1
    const val HOST = "com.kigtts.next"
    const val MANAGER = "com.kigtts.manager"
    const val SERVICE = "com.kigtts.next.manager.PluginAdminService"
    const val PERMISSION = "com.kigtts.next.permission.MANAGE_PLUGINS"
    const val CONTROL = "content://com.kigtts.manager.control"
    const val MAX_JSON = 64 * 1024
    fun authorized(caller: Int, host: Int, sameSignature: Boolean, packages: List<String>) =
        caller == host || sameSignature && MANAGER in packages
}
