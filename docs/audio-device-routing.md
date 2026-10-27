# 首选音频设备

## 通用朗读音量

语音合成服务的「音频设备」组提供通用音量，默认 1 倍，范围 0–10 倍。滑条左半段为 0–1，右半段为 1–10；1 倍附近有吸附点，0 为静音。页面滑动期间只更新预览数值，释放后保存，避免每帧重新加载插件页面。

增益由宿主 PCM16 播放层统一执行，各合成引擎共用，不改变系统音量。超出 PCM 范围的样本饱和处理；大增益会使原本较响的声音失真。设备偏好和音量分别保存，插件 API 仍为 32，无需升级引擎即可生效。

2026-10-27：增益映射、中点吸附、PCM 静音/放大/饱和及原缓冲保持、非法值拒绝与重启持久化已纳入 `SpeechGainTest`、`AudioDeviceRoutingTest`；原生滑条触控和无障碍进度纳入 `PluginControlsUiTest`。本轮未用云端密钥测试真实音频。

## 设备选择

语音识别服务的「音频设备」组可选首选麦克风；语音合成服务可选首选朗读设备。主界面左上角自动朗读按钮长按可切换朗读设备，短按仍切换自动朗读。

输入、输出分别保存。设备断开后暂用系统默认，重新连接后恢复首选项。正在录音或朗读时也会更新偏好；不重启引擎、不切断识别会话。

实现使用主程序统一 PCM 传输的 `AudioRecord` / `AudioTrack.setPreferredDevice`，不修改全系统的通话设备。设备身份使用类型和地址，无地址时使用产品名，避免连接时的临时设备 ID 改变后丢失选择。

Android 将此接口定义为首选设备，并不保证所有设备、格式和蓝牙录音组合都能采用指定路由。实际生效仍由系统决定；不支持时回退默认。参考 [AudioRouting](https://developer.android.com/reference/android/media/AudioRouting#setPreferredDevice(android.media.AudioDeviceInfo)) 和 [设备连接通知](https://developer.android.com/reference/android/media/AudioManager#registerAudioDeviceCallback(android.media.AudioDeviceCallback,%20android.os.Handler))。

验证：`AudioDeviceRoutingTest` 覆盖运行中输入/输出分离、断开回退、重连恢复、重启持久化及仅服务可设置设备；`AutomaticReadDeviceUiTest` 覆盖长按选择与自动朗读状态隔离。与识别、合成协调及服务引导回归合计19项通过，包含 Android 8 和 Android 15 两档设备路由测试，日志 `build/audio-devices-release-final.log`。未操作手机，蓝牙和不同机型的实际路由需实机验收。
