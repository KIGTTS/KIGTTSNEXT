package com.kigtts.next.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.kigtts.plugin.api.*
import com.kigtts.next.i18n.UiLanguage
import com.kigtts.next.i18n.uiCopy

private const val NOISE_ACTION = "host-audio/input-noise/value"
private const val NOISE_PREF = "input-noise-suppression"

/** Per-stream preferences never change the device-wide communication route. */
class AudioDeviceRouting(context: Context, internal var onChanged: () -> Unit = {}) : AutoCloseable {
    private val appContext = context.applicationContext
    @Volatile private var locale = UiLanguage.STANDARD.tag
    internal fun setLocale(value: String) { locale = value }
    private fun copy(key: String, fallback: String) = appContext.uiCopy(key, locale = locale).ifBlank { fallback }
    private val manager = context.getSystemService(AudioManager::class.java)
    private val preferences = context.getSharedPreferences("speech-audio-devices", Context.MODE_PRIVATE)
    private var closed = false
    private val leases = mutableMapOf<AudioRouting, Boolean>()
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    init {
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }
    private fun side(input: Boolean) = if (input) "input" else "output"
    private fun devices(input: Boolean) = manager.getDevices(if (input) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS).toList()
    private fun endpoint(device: AudioDeviceInfo): AudioEndpoint {
        val kindKey = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "audio.device.kind.builtin_mic" to "内置麦克风"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "audio.device.kind.speaker" to "手机扬声器"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "audio.device.kind.earpiece" to "听筒"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "audio.device.kind.bluetooth" to "蓝牙设备"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "audio.device.kind.usb" to "USB 音频"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "audio.device.kind.wired" to "有线耳机"
            else -> "audio.device.kind.generic" to "音频设备"
        }
        val kind = copy(kindKey.first, kindKey.second)
        val name = device.productName?.toString().orEmpty()
        val label = if (device.type in setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) || name.isBlank()) kind else "$name · $kind"
        return AudioEndpoint(audioEndpointKey(device.type, (if (Build.VERSION.SDK_INT >= 28) device.address.orEmpty() else ""), name), label, device.type)
    }
    @Synchronized internal fun endpoints(input: Boolean) = labelAudioEndpoints(devices(input).map(::endpoint).map { it.copy(key = normalizeAudioEndpointKey(it.key)) })
    internal fun selected(input: Boolean): String? = preferences.getString(side(input), null)?.let(::normalizeAudioEndpointKey)
    internal val noiseSuppression: Boolean get() = preferences.getBoolean(NOISE_PREF, false)
    internal val outputGain: Float get() = preferences.getFloat("output-gain", 1f).takeIf { it.isFinite() && it in 0f..10f } ?: 1f
    internal fun setOutputGain(value: Float) {
        require(value.isFinite() && value in 0f..10f) { "音量需在0至10倍之间" }
        preferences.edit().putFloat("output-gain", value).apply()
    }
    fun summary(input: Boolean): String = selected(input)?.let { key ->
        endpoints(input).firstOrNull { it.key == key }?.label ?: (preferences.getString(side(input) + "-name", copy("audio.devices", "音频设备")) + " · " + copy("audio.unavailable", "未连接"))
    } ?: copy("audio.default", "系统默认")
    @Synchronized internal fun select(input: Boolean, key: String?) {
        val endpoint = key?.let { value -> endpoints(input).firstOrNull { it.key == value } ?: error(copy("audio.device.disconnected", "设备已断开，请重新选择")) }
        preferences.edit().putString(side(input), endpoint?.key).putString(side(input) + "-name", endpoint?.label).apply()
    }
    @Synchronized internal fun bind(stream: AudioRouting, input: Boolean) {
        if (closed) return
        leases[stream] = input
        route(stream, input)
    }
    @Synchronized internal fun unbind(stream: AudioRouting) { leases.remove(stream) }
    private fun route(stream: AudioRouting, input: Boolean) {
        val target = preferences.getString(side(input), null)?.let { key ->
            val current = devices(input).map { it to endpoint(it).key }
            current.firstOrNull { it.second == key }?.first
                ?: current.firstOrNull { normalizeAudioEndpointKey(it.second) == normalizeAudioEndpointKey(key) }?.first
        }
        if (runCatching { stream.setPreferredDevice(target) }.getOrDefault(false).not())
            runCatching { stream.setPreferredDevice(null) }
    }
    @Synchronized private fun refresh() {
        if (closed) return
        leases.forEach { (stream, input) -> route(stream, input) }
        onChanged()
    }
    fun decorate(plugin: KigPlugin, page: PluginPage?): PluginPage? {
        if (page?.id != "manager") return page
        val input = when {
            plugin.asr?.info?.role == AsrRole.SERVICE -> true
            plugin.tts?.info?.role == TtsRole.SERVICE -> false
            else -> return page
        }
        val controls = mutableListOf<PageBlock>()
        if (!input) controls += PageBlock.Slider(PluginControl.Slider(copy("audio.gain", "语音合成音量"), SPEECH_GAIN_ACTION, outputGain, 0f, 10f, suffix = "×"))
        controls +=
            PageBlock.Select(if (input) copy("audio.preferred_mic", "首选麦克风") else copy("audio.preferred_speech", "首选朗读设备"), actions(input),
                action(input, selected(input)), if (input) PluginIcon.MIC else PluginIcon.SPEAKER)
        if (input) controls += PageBlock.Toggle(copy("audio.noise_suppression", "系统降噪"), noiseSuppression, NOISE_ACTION,
            if (systemRecognitionNoiseAvailable()) copy("audio.noise_next_recording", "下次开始录音时生效") else copy("audio.noise_unsupported", "当前设备不支持系统降噪"), PluginIcon.MIC)
        return page.copy(blocks = page.blocks + PageBlock.Group(copy("audio.group", "音频设备"), controls))
    }
    private fun action(input: Boolean, key: String?) = "host-audio/${side(input)}/" + (key ?: "default")
    private fun actions(input: Boolean): List<PageAction> {
        val available = endpoints(input)
        val selected = selected(input)
        return listOf(PageAction(action(input, null), copy("audio.default", "系统默认"))) +
            available.map { PageAction(action(input, it.key), it.label) } +
            if (selected != null && available.none { it.key == selected })
                listOf(PageAction(action(input, selected), summary(input))) else emptyList()
    }
    fun handle(plugin: KigPlugin, action: String, inputs: Map<String, String> = emptyMap()): Boolean {
        if (!action.startsWith("host-audio/")) return false
        if (action == NOISE_ACTION) {
            require(plugin.asr?.info?.role == AsrRole.SERVICE)
            val enabled = requireNotNull(inputs["checked"]?.toBooleanStrictOrNull()) { "无效的降噪设置" }
            require(!enabled || systemRecognitionNoiseAvailable()) { "当前设备不支持系统降噪" }
            preferences.edit().putBoolean(NOISE_PREF, enabled).apply()
            return true
        }
        if (action == SPEECH_GAIN_ACTION) {
            require(plugin.tts?.info?.role == TtsRole.SERVICE)
            val value = inputs["value"]?.toFloatOrNull()
            require(value != null && value.isFinite() && value in 0f..10f) { "音量需在0至10倍之间" }
            setOutputGain(value)
            return true
        }
        val parts = action.split('/', limit = 3)
        require(parts.size == 3)
        val input = when (parts[1]) { "input" -> true; "output" -> false; else -> error(copy("audio.device.invalid", "音频设备选项无效")) }
        require(if (input) plugin.asr?.info?.role == AsrRole.SERVICE else plugin.tts?.info?.role == TtsRole.SERVICE)
        val key = parts[2].takeUnless { it == "default" }
        if (key != selected(input)) select(input, key)
        return true
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        manager.unregisterAudioDeviceCallback(callback)
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        leases.clear()
    }
}
