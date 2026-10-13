package com.kigtts.next.ui.cards.camera

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal enum class CameraFlash(val label: String) { OFF("关闭"), AUTO("自动"), ON("开启") }
internal enum class CameraGrid(val label: String) { OFF("关闭"), THIRDS("三分线"), SQUARE("方格") }

/** Camera preferences are independent of the finished photograph and its paper frame. */
internal class CameraOptions(private val preferences: SharedPreferences) {
    private var selectedGrid by mutableStateOf(enumPreference("grid", CameraGrid.THIRDS))
    private var showLevel by mutableStateOf(preferences.getBoolean("level", true))
    private var selectedFlash by mutableStateOf(enumPreference("flash", CameraFlash.OFF))
    private var selectedProcessing by mutableStateOf(enumPreference("processing", CameraProcessing.STANDARD))
    private var selectedStrength by mutableFloatStateOf(preferences.getFloat("filter-strength", 1f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f)
    val grid get() = selectedGrid
    val level get() = showLevel
    val flash get() = selectedFlash
    val processing get() = selectedProcessing
    val filterStrength get() = selectedStrength
    var timerSeconds by mutableIntStateOf(preferences.getInt("timer", 0).takeIf { it in TIMERS } ?: 0); private set
    var settingsVisible by mutableStateOf(false); private set

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == preferences.getString(key, null) } ?: fallback
    fun setGrid(value: CameraGrid) { selectedGrid = value; preferences.edit().putString("grid", value.name).apply() }
    fun setLevel(value: Boolean) { showLevel = value; preferences.edit().putBoolean("level", value).apply() }
    fun setFlash(value: CameraFlash) { selectedFlash = value; preferences.edit().putString("flash", value.name).apply() }
    fun setProcessing(value: CameraProcessing) { selectedProcessing = value; preferences.edit().putString("processing", value.name).apply() }
    fun setFilterStrength(value: Float) {
        if (!value.isFinite()) return
        selectedStrength = value.coerceIn(0f, 1f); preferences.edit().putFloat("filter-strength", selectedStrength).apply()
    }
    fun setTimer(value: Int) {
        require(value in TIMERS)
        timerSeconds = value; preferences.edit().putInt("timer", value).apply()
    }
    fun showSettings() { settingsVisible = true }
    fun hideSettings() { settingsVisible = false }
    companion object { val TIMERS = listOf(0, 3, 10) }
}

@Composable
internal fun rememberCameraOptions(): CameraOptions {
    val context = LocalContext.current.applicationContext
    return remember(context) { CameraOptions(context.getSharedPreferences("polaroid_camera", 0)) }
}
