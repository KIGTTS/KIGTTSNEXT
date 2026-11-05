package com.kigtts.next.ui.cards.camera

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.media.CameraPhotoEncoding
import com.kigtts.next.media.color.PhotoEffects

internal enum class CameraFlash(val label: String) { OFF("关闭"), AUTO("自动"), ON("开启") }
internal enum class CameraGrid(val label: String) { OFF("关闭"), THIRDS("三分线"), SQUARE("方格") }

/** Camera preferences are independent of the finished photograph and its paper frame. */
internal class CameraOptions(private val preferences: SharedPreferences) {
    private var selectedGrid by mutableStateOf(enumPreference("grid", CameraGrid.THIRDS))
    private var showLevel by mutableStateOf(preferences.getBoolean("level", true))
    private var selectedFlash by mutableStateOf(enumPreference("flash", CameraFlash.OFF))
    private var selectedProcessing by mutableStateOf(enumPreference("processing", CameraProcessing.STANDARD))
    private var motionEnabled by mutableStateOf(preferences.getBoolean("motion", false))
    var encoding by mutableStateOf(CameraPhotoEncoding.read(preferences.getString(ENCODING_KEY, null))); private set
    private var selectedStrength by mutableFloatStateOf(preferences.getFloat("filter-strength", 1f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f)
    var aspect by mutableStateOf(enumPreference("aspect", CameraAspect.STANDARD)); private set
    var mode by mutableStateOf(CameraMode.PHOTO); private set
    val framingAspect get() = if (mode == CameraMode.SCAN) CameraAspect.STANDARD else aspect
    var watermark by mutableStateOf(preferences.getString("watermark", "none") ?: "none"); private set
    var watermarkStyle by mutableStateOf(watermark.takeUnless { it == "none" }
        ?: preferences.getString("watermark-style", "classic").orEmpty()); private set
    fun chooseAspect(value: CameraAspect) { aspect = value; preferences.edit().putString("aspect", value.name).apply() }
    fun chooseMode(value: CameraMode) { mode = value }
    fun chooseWatermark(value: String) {
        watermark = value
        preferences.edit().putString("watermark", value).apply {
            if (value != "none") { watermarkStyle = value; putString("watermark-style", value) }
        }.apply()
    }
    val grid get() = selectedGrid
    val level get() = showLevel
    val flash get() = selectedFlash
    val processing get() = if (motionEnabled) CameraProcessing.STANDARD else selectedProcessing
    val motion get() = motionEnabled
    val filterStrength get() = selectedStrength
    private var selectedEffects by mutableStateOf(PhotoEffects.from(listOf("vignette", "grain", "aberration", "bloom").map { preferences.getFloat("effect-$it", 0f) }))
    val effects get() = selectedEffects
    fun setEffects(value: PhotoEffects) {
        selectedEffects = value
        preferences.edit().apply { listOf("vignette", "grain", "aberration", "bloom").zip(value.values().toList()).forEach { (key, amount) -> putFloat("effect-$key", amount) } }.apply()
    }
    var timerSeconds by mutableIntStateOf(preferences.getInt("timer", 0).takeIf { it in TIMERS } ?: 0); private set
    var settingsVisible by mutableStateOf(false); private set

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == preferences.getString(key, null) } ?: fallback
    fun setGrid(value: CameraGrid) { selectedGrid = value; preferences.edit().putString("grid", value.name).apply() }
    fun setLevel(value: Boolean) { showLevel = value; preferences.edit().putBoolean("level", value).apply() }
    fun setFlash(value: CameraFlash) { selectedFlash = value; preferences.edit().putString("flash", value.name).apply() }
    fun setProcessing(value: CameraProcessing) { selectedProcessing = value; preferences.edit().putString("processing", value.name).apply() }
    fun chooseEncoding(value: CameraPhotoEncoding) {
        encoding = value
        if (value == CameraPhotoEncoding.HEIF) motionEnabled = false
        preferences.edit().putString(ENCODING_KEY, value.name).putBoolean("motion", motionEnabled).apply()
    }
    fun setMotion(value: Boolean) {
        motionEnabled = value
        if (value) encoding = CameraPhotoEncoding.JPEG
        preferences.edit().putBoolean("motion", value).putString(ENCODING_KEY, encoding.name).apply()
    }
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
    companion object {
        val TIMERS = listOf(0, 3, 10)
        const val ENCODING_KEY = "photo-encoding"
    }
}

@Composable
internal fun rememberCameraOptions(): CameraOptions {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("polaroid_camera", 0) }
    return rememberSaveable(saver = Saver<CameraOptions, String>(
        save = { it.mode.name },
        restore = { saved -> CameraOptions(preferences).apply { chooseMode(CameraMode.valueOf(saved)) } },
    )) { CameraOptions(preferences) }
}
