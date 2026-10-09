# 专用插件包与引擎迁移

## 独立构建

主程序 APK 不携带语音引擎。Piper、SenseVoice、增量流式识别、语音合成服务都是独立插件；`system-support` 仅供插件构建时复用系统 TTS 实现，实际内置在服务包。旧 `system-tts` 独立包保留兼容选项，使用服务时无需启用它。依赖不进入 app 的生产依赖图。Android 专用 API 仅提供工作进程 Context 与统一数据目录，不包含引擎 SDK。

使用 PowerShell 7、JDK 17 和 Android SDK 36：

```powershell
pwsh -File tools/Build-EnginePlugins.ps1
```

本机脚本使用 `.tools/gradle-8.13` 和 `.tools/android-sdk`。输出在 `build/plugin-packages/`：`service.speech-synthesis-0.4.0.kigplugin`、`service.speech-recognition-0.4.0.kigplugin`、`engine.piper-0.4.0.kigplugin`、`engine.sensevoice-0.4.0.kigplugin`、`engine.streaming-asr-0.4.0.kigplugin`、旧兼容 `engine.system-tts-0.4.0.kigplugin`。可用 `-Modules streaming-asr` 只构建指定模块；也可先单独构建 Android library，再加 `-SkipBuild` 打包。默认在当前用户 LocalAppData/Temp/KIGTTSNEXT 中保留解包中间文件，可用 `-WorkDirectory` 指定构建临时目录。脚本只把插件自己的 classes.jar、所需引擎依赖和本地库交给 D8，API/Kotlin/协程由工作进程的父加载器提供。

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

API v5 当前运行时为 `android-dex`。API2/API3/API4 包与新主程序不兼容，需用同一发布者的 0.4.0/API5 包更新；更新保留对应数据，勿通过卸载升级已导入模型的引擎。Lua/Python 仍是后续可增加的运行时，当前不支持脚本安装。插件不能在安装时添加 Activity、Service 或 Android 资源 ID；界面使用公共声明式协议，由宿主的 MD3e 组件渲染。

## 安装与生命周期

从“设置 → 插件管理 → 安装 .kigplugin”选取本地文件。首次安装展示名称、版本、许可证、能力与发布者指纹，用户确认可信来源后安装。签名验证涵盖全部有效载荷，安装与加载均检查完整性；拒绝目录穿越、重复路径、清单外文件、超限资源、不兼容 API/ABI、降级更新和发布者变化。Android 14+ 的代码文件在打开写入通道后、写入前置为只读。

安装完成后在独立工作进程加载入口，调用 attach，再建立 UI/引擎代理。PCM 通过二进制管道传输，具有有界背压，不把大录音放进 Binder 事务。主进程只保存接口数据、代理和规范音频传输。

停用保留数据；卸载删除该插件包、下载、模型、配置、缓存和临时文件。更新、资源导入、停用和卸载前结束宿主音频会话并销毁对应工作进程，避免已加载类、本地库和模型映射继续使用。每个外部插件拥有单独进程，当前最多同时启用 8 个。应用退至后台停止录音/播放，插件管理器销毁时释放所有进程。进程崩溃后的管道中断会报告任务失败，停用后重新启用可恢复。

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

- 语音合成服务：统一引擎目录、声音分页、语速与系统语言配置。自身内置系统 TTS，未导入 Piper 时可直接使用；外部引擎停用/缺模型/合成前失败时保底，取消不重播，已输出音频的当前分段失败不重复朗读。
- Piper：移植旧 ONNX、eSpeak、中文拼音/日文读音与跨语言近似适配；支持旧 `.kigvpk`/ZIP manifest 与标准 ONNX+同名 JSON ZIP、嵌套目录、多个声音、导入/选择/重命名/二次确认删除语音包。模型属于 `engine.piper/models/voices`，发音数据属于 `engine.piper/resources`，不会混入服务或主程序。首版完整本地库仅支持 arm64-v8a。源码、固定 eSpeak 完整源归档与独立构建参考在 Piper 的 `native-source/`；本次使用原工程预编译库，独立 NDK 构建入口尚未实编。
- 系统 TTS：沿用原版系统服务初始化、候选回退和文件合成流程；改成可取消等待、超时、规范 PCM16 WAV 读取及插件临时文件清理。依赖设备安装的系统 TTS 服务和相应语言数据。
- SenseVoice：沿用原版 sherpa 离线配置、16 kHz 单声道输入和文件模型加载；完整录音接口最多 30 秒。分段流式入口基于音量端点、500 ms 静音与最长 12 秒切段，逐句执行离线推理、发布 Final，输入结束冲刷剩余语句。没有凭空制造 Partial 文本，也不宣称原生增量解码。
- SenseVoice 模型须自行导入 ONNX 与同目录的 tokens.txt，或包含二者的 ZIP；支持原资源包的嵌套目录。7z、Kokoro、降噪、声纹、AEC 和说话人分离没有在这一版迁移，应作为后续独立插件迭代。

移植插件与系统共享实现保留原工程 GPLv3 许可与来源；主程序/API/通用运行时保持 MIT。sherpa/ONNX Runtime、eSpeak、发音器和词典的第三方许可随插件包保存，模型许可随模型资源保留。

## 使用语音合成服务

先安装并启用服务包，再安装并启用 Piper；服务会成为统一朗读入口。通过右侧抽屉“语音合成服务 → Piper → 导入语音包”选择 ZIP/.kigvpk，导入后选用声音。删除需确认，并删除该语音包的所有声音。没有 Piper 包时服务仍能调用自身的系统 TTS，旧 `engine.system-tts` 可停用以节省工作进程。

系统 TTS 的“管理引擎与语音包 → 刷新声音列表”读取 Android 已安装声音，支持选择具体声音；系统语音数据本身由系统 TTS 应用安装。导入文件临时经过服务 `resources/inbox`，broker 的副本在 Piper `tmp`，完成或失败均清理；下载、外置文件、模型、词典和配置都留在各插件 Appdata 子目录。

## 验证范围

单元与原生 UI 回归覆盖包签名/实际产物、发布者/版本/完整性、额外 DEX、数据路径/越界、导入回滚/清理、二进制 PCM 帧、无模型不打开麦克风、规范引擎调度与取消、端点分段、RIFF 解码、以及主 APK 无引擎类/库检查。

上一版 API2 的 Android 16 真机已执行实际 DEX/JNI/ONNX 加载、系统 TTS 合成（84,096 字节 PCM）和宿主 AudioTrack 播放、停用/重新启用、资源导入、完整录音与分段流式识别，以及取消流式后再次完整识别，最终 OK（1 项综合仪器测试，16.646 秒）。当时两个插件及模型已保留并启用，测试辅助包与手机上的测试暂存文件已移除。本次 API3 更新后的验证另列于下；旧包已同发布者升级，原有模型与配置保留。

测试使用官方固定版本 SenseVoice int8 模型（提交 2365baeacb507f821a0c8120fcee3d484dba7a07，model SHA-256 c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51）及 zh.wav。源 https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17 。模型/tokens/LICENSE 存在插件 models 下，没有进入主 APK 或 Git。

固定录音完整识别为“开放时间早上9点至下午5点。”，包括初次模型加载约 1823 ms；分段结果为“开饭时间早上9点至下午5点。”，存在一处错字。功能链路通过不代表模型精度验收通过。现场麦克风、嘈杂环境与长时间会话需要后续验证。

真机检查修复了两个宿主问题：ART 自动生成的 code/oat 不是签名有效载荷，加载前使用不跟随符号链接的清理方法删除缓存，仍拒绝额外 DEX；输入管道改用普通管道和显式 END_INPUT，避免可靠管道在 AIDL 参数复制时误报远端死亡，缺少结束帧仍报告输入中断。

烟测位于 app/src/androidTest 的 PluginRuntimeDeviceTest。先打包插件，再构建 :app:assembleDebugAndroidTest，安装主 APK 和测试 APK（-t），通过 AndroidJUnitRunner 运行。keepPackages=true 保留本次安装包；playAudio=true 测试实际播放。resourceZip 与 sampleWav 参数必须指向对应插件数据目录，可增加模型导入和识别测试。测试包里的引擎 ZIP 仅存在于 AndroidTest APK，主 APK 没有这些资产。默认只清理自己创建的插件。

新增 `SpeechServiceDeviceTest` 验证服务内置系统 TTS、经 broker 的 Piper 实际 DEX/JNI/PCM、语音包导入/选用/重命名/删除确认、取消后重用以及停用提供者后的保底。执行时必须传 `piperVoiceZip`，指向 Piper 对应数据目录中的测试语音包。可传 `keepPackages=true` 保留安装的插件与声音，`playAudio=true` 播放音频；测试会同签名更新旧包、停用旧独立系统 TTS。没有测试模型时不声称 Piper 验证通过。

本次 API3 真机两项综合仪器检查全部通过：SpeechServiceDeviceTest 用时 20.535 秒，内置系统 TTS 93,312 字节 PCM、经服务/broker 的 Piper DEX/JNI/eSpeak/ORT 55,296 字节 PCM，均通过宿主实际播放；Piper 取消后重用 56,320 字节，停用提供者后系统保底 87,552 字节。语音包导入/选用/重命名/删除确认链路通过，Piper 使用旧工程的英文开发测试模型，仅作功能检查，不能作为中文或自然度验收。

SpeechUpgradeDeviceTest 用时 6.689 秒，直接复用原已安装 SenseVoice 模型，完整录音结果“开放时间早上9点至下午5点。”；分段仍为“开饭时间早上9点至下午5点。”，同一处识别错字仍在。服务默认已恢复内置系统 TTS；Piper、SenseVoice、语音合成服务启用，旧独立系统插件 0.2.0 已更新并停用。Piper 测试声音保留在对应模型目录，测试辅助 APK 与识别录音暂存已清理，主应用冷启动通过。171 项本地测试与签名/载荷检查通过，主 APK 没有任何引擎实现/库/模型/插件包资产；本次只验证 arm64 真机，独立 NDK 构建入口未实编。

## API4 统一管理与 Piper 兼容

“语音包管理”由语音合成服务提供，所有引擎的包放在同一列表，可筛选、置顶、长按/手柄排序并保存跨引擎顺序。停用引擎已知的包仍可查看和排序，重新启用后才可使用/编辑/导出。语音包与声音分层，多声音只占一条包记录；系统 TTS 为虚拟包，可在详情直接刷新系统声音。插件管理采用同一紧凑列表，开关与拖动手柄同行，顺序重启后保留；它仅改变管理页排列，不改变加载或手势优先级。

Piper 兼容旧 .kigvpk/ZIP 的 manifest.files.model/config/phonemizer、Windows 路径分隔符、标量/数组音素映射、词典与 eSpeak、多语言近似适配、采样率和多声音。旧语音包真正的元数据文件是 voicepack.json；完整保留名称、备注、头像、置顶、顺序与未知字段，支持 .bak 恢复，也合并迁移上一版 voice-meta.json。旧包未声明 phoneme_type 而带词典时按旧版走 text，标准 ONNX+JSON 则默认 eSpeak。

详情可修改名称/多行备注、替换头像、更新包、导出与确认删除。更新保持包 ID、选择和用户资料，先验证后交换，失败恢复旧数据。导出保留模型、词典、预览与其它原文件，并写出旧版可接受的 manifest/config/phonemizer 三件组和 voicepack.json；标准 eSpeak 包补明确类型和空词典，旧版不会误判为 text。跨引擎排序的当前位置和置顶状态随导出保存。所有操作仍属于引擎/服务插件，宿主只渲染通用条目并转发规范命令。

API4 功能的本地回归与新包产物单独验证；前文两项 Android 16 真机检查是 API3 的结果。本次设备暂未连接，不能把旧检查作为 API4 新管理功能的真机验收。手机原有 API3 仍保持可用，本轮没有单独覆盖主 APK 导致插件不兼容。

## 增量流式识别插件

engine.streaming-asr 的在线解码、多语言包管理、固定校验下载目录与 .kigasr 格式见 [流式识别使用说明](streaming-recognition.md)。当前 API5，不改变模型包格式；新插件 MIT，现有原版移植插件保留 GPLv3。复用宿主 ManagementList，不把模型/导入器/推理库放入主 APK。

## 独立识别服务（API5）

[语音识别服务](recognition-service.md)管理独立的 SenseVoice / 增量流式及社区 ASR 提供者，字幕和语音输入依赖它。服务没有推理库/模型；全部原引擎同签名重建为 0.4.0，以兼容角色、依赖、broker 和明确的结束帧。当前六个外部包均需 API5 主程序配合，升级保留引擎数据目录。
