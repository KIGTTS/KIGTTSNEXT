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

/** Per-stream preferences never change the device-wide communication route. */
class AudioDeviceRouting(context: Context, internal var onChanged: () -> Unit = {}) : AutoCloseable {
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
        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "内置麦克风"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "手机扬声器"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "蓝牙设备"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 音频"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
            else -> "音频设备"
        }
        val name = device.productName?.toString().orEmpty()
        val label = if (device.type in setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) || name.isBlank()) kind else "$name · $kind"
        return AudioEndpoint(audioEndpointKey(device.type, (if (Build.VERSION.SDK_INT >= 28) device.address.orEmpty() else ""), name), label, device.type)
    }
    @Synchronized internal fun endpoints(input: Boolean) = devices(input).map(::endpoint).distinctBy { it.key }
    internal fun selected(input: Boolean): String? = preferences.getString(side(input), null)
    internal val outputGain: Float get() = preferences.getFloat("output-gain", 1f).takeIf { it.isFinite() && it in 0f..10f } ?: 1f
    fun summary(input: Boolean): String = selected(input)?.let { key ->
        endpoints(input).firstOrNull { it.key == key }?.label ?: (preferences.getString(side(input) + "-name", "音频设备") + "（未连接）")
    } ?: "系统默认"
    @Synchronized internal fun select(input: Boolean, key: String?) {
        val endpoint = key?.let { value -> endpoints(input).firstOrNull { it.key == value } ?: error("设备已断开，请重新选择") }
        preferences.edit().putString(side(input), endpoint?.key).putString(side(input) + "-name", endpoint?.label).apply()
    }
    @Synchronized internal fun bind(stream: AudioRouting, input: Boolean) {
        if (closed) return
        leases[stream] = input
        route(stream, input)
    }
    @Synchronized internal fun unbind(stream: AudioRouting) { leases.remove(stream) }
    private fun route(stream: AudioRouting, input: Boolean) {
        val target = selected(input)?.let { key -> devices(input).firstOrNull { endpoint(it).key == key } }
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
        if (!input) controls += PageBlock.Slider(PluginControl.Slider("音量", SPEECH_GAIN_ACTION, outputGain, 0f, 10f, suffix = "×"))
        controls +=
            PageBlock.Select(if (input) "首选麦克风" else "首选朗读设备", actions(input),
                action(input, selected(input)), if (input) PluginIcon.MIC else PluginIcon.SPEAKER)
        return page.copy(blocks = page.blocks + PageBlock.Group("音频设备", controls))
    }
    private fun action(input: Boolean, key: String?) = "host-audio/${side(input)}/" + (key ?: "default")
    private fun actions(input: Boolean) = listOf(PageAction(action(input, null), "系统默认")) +
        endpoints(input).map { PageAction(action(input, it.key), it.label) } +
        if (selected(input) != null && endpoints(input).none { it.key == selected(input) })
            listOf(PageAction(action(input, selected(input)), summary(input))) else emptyList()
    fun handle(plugin: KigPlugin, action: String, inputs: Map<String, String> = emptyMap()): Boolean {
        if (!action.startsWith("host-audio/")) return false
        if (action == SPEECH_GAIN_ACTION) {
            require(plugin.tts?.info?.role == TtsRole.SERVICE)
            val value = inputs["value"]?.toFloatOrNull()
            require(value != null && value.isFinite() && value in 0f..10f) { "音量需在0至10倍之间" }
            preferences.edit().putFloat("output-gain", value).apply()
            return true
        }
        val parts = action.split('/', limit = 3)
        require(parts.size == 3)
        val input = when (parts[1]) { "input" -> true; "output" -> false; else -> error("音频设备选项无效") }
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
