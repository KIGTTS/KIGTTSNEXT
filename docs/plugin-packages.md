# 专用插件包与引擎迁移

## 独立构建

主程序 APK 不携带语音引擎。`engine-plugins/system-tts` 和 `engine-plugins/sensevoice-asr` 是独立源码目录，依赖不进入 app 的生产依赖图。Android 专用 API 仅提供工作进程 Context 与统一数据目录，不包含引擎 SDK。

使用 PowerShell 7、JDK 17 和 Android SDK 36：

```powershell
pwsh -File tools/Build-EnginePlugins.ps1
```

本机脚本使用 `.tools/gradle-8.13` 和 `.tools/android-sdk`。输出为 `build/plugin-packages/engine.system-tts-0.1.0.kigplugin` 与 `engine.sensevoice-0.1.0.kigplugin`。也可先单独构建两个 Android library，再加 `-SkipBuild` 打包。脚本只把插件自己的 classes.jar、所需引擎依赖和本地库交给 D8，API/Kotlin/协程由工作进程的父加载器提供。

首次打包在 `.tools/plugin-signing/private.der` 生成本地 RSA 发布者私钥，私钥不入 Git。保留该文件用于后续更新；正式发布可通过 `-SigningKey` 指定自己的 PKCS#8 DER 私钥。更新包必须由同一发布者签名，并递增 `plugin-spec.json` 的 versionCode；descriptor.version 与清单版本必须一致。

## 包结构

```text
plugin.json                 # API、入口、版本、能力、ABI、每个文件的 SHA-256
code/classes.dex            # Android DEX；不是普通 JVM JAR
code/classes2.dex           # 可选多 DEX
lib/<ABI>/*.so              # 插件专属本地库
assets/                     # 只读的内置资源与许可证
signature/public.der        # RSA SubjectPublicKeyInfo
signature/manifest.sig      # 原始 plugin.json 的 SHA256withRSA 签名
```

API v2 首版运行时为 `android-dex`。Lua/Python 仍是后续可增加的运行时，当前不支持脚本安装。插件不能在安装时添加 Activity、Service 或 Android 资源 ID；界面使用公共声明式协议，由宿主的 MD3e 组件渲染。

## 安装与生命周期

从“设置 → 插件管理 → 安装 .kigplugin”选取本地文件。首次安装展示名称、版本、许可证、能力与发布者指纹，用户确认可信来源后安装。签名验证涵盖全部有效载荷，安装与加载均检查完整性；拒绝目录穿越、重复路径、清单外文件、超限资源、不兼容 API/ABI、降级更新和发布者变化。Android 14+ 的代码文件在打开写入通道后、写入前置为只读。

安装完成后在独立工作进程加载入口，调用 attach，再建立 UI/引擎代理。PCM 通过二进制管道传输，具有有界背压，不把大录音放进 Binder 事务。主进程只保存接口数据、代理和规范音频传输。

停用保留数据；卸载删除该插件包、下载、模型、配置、缓存和临时文件。更新、资源导入、停用和卸载前结束宿主音频会话并销毁对应工作进程，避免已加载类、本地库和模型映射继续使用。每个外部插件拥有单独进程，首版最多同时启用 4 个。应用退至后台停止录音/播放，插件管理器销毁时释放所有进程。进程崩溃后的管道中断会报告任务失败，停用后重新启用可恢复。

这些进程与宿主使用相同 Android UID，属于崩溃隔离，不是权限沙箱。能力声明约束公开接口；它不能阻止任意 DEX/JNI 直接调用 Android、网络或文件 API。当前仅安装可信插件；真正面向不可信代码的 isolated UID/能力代理需另行实现和验证，不能把文件路径约定当作安全隔离。

## 数据目录

所有插件网络与外置资源归属：

```text
<应用专属外置文件目录>/appdata/Kigtts/plugin/plugindata/<pluginId>/
  config/
  models/
  resources/
  downloads/
  tmp/
  files/
  cache/
```

常见完整路径为 `/storage/emulated/0/Android/data/com.kigtts.next/files/appdata/Kigtts/plugin/plugindata/<pluginId>/`。外置目录不可用时在应用内部目录保留相同相对路径。资源导入先复制到该插件 tmp，解压也在该目录内完成，成功后替换目标；失败清理暂存并保留已有文件。插件包的代码/只读资源安装在内部 `files/kigplugin/packages/`，与可写的模型/下载分离。

PluginStorage 只接受相对路径并检查规范路径；下载接口要求 HTTPS，检查重定向、限制大小、取消后清理临时文件，并支持 SHA-256。SenseVoice 下载入口强制填写发布者提供的 SHA-256，不在后台自动下载。插件不应使用宿主的普通 SharedPreferences、公共 Download 或自建散落缓存路径。

## 本次引擎

- 系统 TTS：沿用原版系统服务初始化、候选回退和文件合成流程；改成可取消等待、超时、规范 PCM16 WAV 读取及插件临时文件清理。依赖设备安装的系统 TTS 服务和相应语言数据。
- SenseVoice：沿用原版 sherpa 离线配置、16 kHz 单声道输入和文件模型加载；完整录音接口最多 30 秒。分段流式入口基于音量端点、500 ms 静音与最长 12 秒切段，逐句执行离线推理、发布 Final，输入结束冲刷剩余语句。没有凭空制造 Partial 文本，也不宣称原生增量解码。
- 模型须自行导入 ONNX 与同目录的 tokens.txt，或包含二者的 ZIP；支持原资源包的嵌套目录。7z、Piper/Kokoro、降噪、声纹、AEC 和说话人分离没有在这一版迁移，应作为后续独立插件迭代。

两个移植插件保留原工程 GPLv3 许可与来源；主程序/API/通用运行时保持 MIT。sherpa/ONNX Runtime 的第三方许可随插件包保存，模型许可随模型资源保留。

## 验证范围

单元与原生 UI 回归覆盖包签名/实际产物、发布者/版本/完整性、额外 DEX、数据路径/越界、导入回滚/清理、二进制 PCM 帧、无模型不打开麦克风、规范引擎调度与取消、端点分段、RIFF 解码、以及主 APK 无引擎类/库检查。

Android 16 真机已执行实际 DEX/JNI/ONNX 加载、系统 TTS 合成（84,096 字节 PCM）和宿主 AudioTrack 播放、停用/重新启用、资源导入、完整录音与分段流式识别，以及取消流式后再次完整识别，最终 OK（1 项综合仪器测试，16.646 秒）。两个插件及模型已保留并启用，测试辅助包与手机上的测试暂存文件已移除。

测试使用官方固定版本 SenseVoice int8 模型（提交 2365baeacb507f821a0c8120fcee3d484dba7a07，model SHA-256 c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51）及 zh.wav。源 https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17 。模型/tokens/LICENSE 存在插件 models 下，没有进入主 APK 或 Git。

固定录音完整识别为“开放时间早上9点至下午5点。”，包括初次模型加载约 1823 ms；分段结果为“开饭时间早上9点至下午5点。”，存在一处错字。功能链路通过不代表模型精度验收通过。现场麦克风、嘈杂环境与长时间会话需要后续验证。

真机检查修复了两个宿主问题：ART 自动生成的 code/oat 不是签名有效载荷，加载前使用不跟随符号链接的清理方法删除缓存，仍拒绝额外 DEX；输入管道改用普通管道和显式 END_INPUT，避免可靠管道在 AIDL 参数复制时误报远端死亡，缺少结束帧仍报告输入中断。

烟测位于 app/src/androidTest 的 PluginRuntimeDeviceTest。先打包插件，再构建 :app:assembleDebugAndroidTest，安装主 APK 和测试 APK（-t），通过 AndroidJUnitRunner 运行。keepPackages=true 保留本次安装包；playAudio=true 测试实际播放。resourceZip 与 sampleWav 参数必须指向对应插件数据目录，可增加模型导入和识别测试。测试包里的引擎 ZIP 仅存在于 AndroidTest APK，主 APK 没有这些资产。默认只清理自己创建的插件。
