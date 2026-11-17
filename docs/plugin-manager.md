# 独立插件管理器

`com.kigtts.manager` 是安装一次、反复使用的独立 Android APK，用于 KIGTTS 插件安装、调试和状态监控。管理器不包含语音引擎、模型、插件 SDK 或插件包；更新 `.kigplugin` 时无需重装管理器或测试 APK。修改主程序、管理器自身或管理协议时，仍需更新对应 APK。

## 使用

构建 `:app:assembleDebug :plugin-manager:assembleDebug`，安装主程序与 `plugin-manager/build/outputs/apk/debug/plugin-manager-debug.apk`。两者须使用相同 Android 签名证书，Release 也需配置相同签名。插件包的发布者签名独立于 APK 签名，安装继续使用原有完整包验证与发布者核对。

管理器提供包检查/签名确认、安装/更新、启停、重载、确认卸载、动作 ID + JSON 调试、合成 PCM 自检和操作记录。列表显示插件版本/API、启用/加载状态、实际工作进程 PID 与错误；内置界面插件可调试，但不允许从该界面卸载。卸载会删除该插件模型、配置和外置资源，必须明确确认。更新保留原启停状态及资源目录。

默认仅在管理器前台刷新。开启“持续监控”后，以用户可停止的前台通知每 3 秒读状态；不自动重启崩溃引擎，不开机自启。系统仍可终止进程，Android/厂商后台限制仍适用。小米等系统若拒绝绑定，请先打开主程序，或允许主程序后台启动；连接失败会明确提示。

主程序 UI 与管理桥共享同一个插件运行时，避免两个管理器分别占用同一工作进程槽。管理器通过文件描述符传文件，不能直接读写主程序私有目录。插件模型/网络/外置文件仍归主程序对应的 `appdata/Kigtts/plugin/plugindata/<pluginId>/`；管理器只在自身 `appdata/Kigtts/plugin-manager/inbox` 保存临时导入包。

## ADB 调试入口

Debug 管理器额外提供 `content://com.kigtts.manager.control`，要求 Android `DUMP` 权限，普通第三方应用不能调用；Release 不包含此 Provider。使用 PowerShell 7 的 [Invoke-PluginManager.ps1](../tools/Invoke-PluginManager.ps1)：

```powershell
./tools/Invoke-PluginManager.ps1 -Command snapshot
./tools/Invoke-PluginManager.ps1 -Command inspect -File build/plugin-packages/engine.piper-0.8.0.kigplugin
./tools/Invoke-PluginManager.ps1 -Command install -File build/plugin-packages/engine.piper-0.8.0.kigplugin -Payload '{"publisher":"检查结果中的签名摘要"}'
./tools/Invoke-PluginManager.ps1 -Command restart -Payload '{"id":"engine.piper"}'
./tools/Invoke-PluginManager.ps1 -Command tts-test -Payload '{"id":"service.speech-synthesis"}'
./tools/Invoke-PluginManager.ps1 -Command asr-test -File fixture-16k-mono-s16le.pcm -Payload '{"id":"engine.streaming-asr"}'
./tools/Invoke-PluginManager.ps1 -Command execute -Payload '{"id":"debug.ui","action":"动作ID","inputs":{}}'
```

支持 `-Device` 选择设备。`resource` 传 `id/directory/name/zip` 并带 `-File`，文件由宿主原资源导入器限定到指定插件目录。`present` 传 `mime` 并带图片、动态照片、视频或 HTML 文件，打开主程序呈现，用于真实媒体检查；`select-media`/`remove-media` 传媒体 `id`，测试结束应仅移除此次导入项。

桥 1 另提供可选 `navigate`（`page` 为 home/settings/appearance/feedback/experimental/plugins/media/sketch/cards，或 plugin 并传 id/pageId）与 `back`，用于在真实主程序查看页面和返回动画，不需要测试 APK，也不修改插件安装状态；旧管理器客户端仍可使用。

命令先入队并返回作业 ID，再查询结果；JSON 使用 UTF-8 Base64，避免 shell 解释内容。操作完成后 CLI 清除自己的传输文件。超时表示结果未知，保留文件和作业 ID，须先检查结果/状态；不能直接再次提交安装或卸载。作业记录最多 50 条、仅在管理器进程中保留，进程退出后缺失也不能据此断言操作未完成。

## 管理协议 1

`:manager-protocol` 是独立于引擎/工具插件 API 的稳定 AIDL 接口，只传版本化 JSON 和可选 FD。主程序服务同时验证 `signature` 权限、调用者签名以及指定包名；同签名的其它应用也不被接受。请求 JSON ≤64 KiB，普通传入文件 ≤768 MiB，插件资源 ≤2 GiB。动作调试和合成自检限时 30 秒，异常作为结果返回，临时宿主文件最终回收。

`execute` 返回原始插件动作结果供检查，不自动执行其网页/导航等宿主命令。`tts-test` 检查有效 PCM 及完成事件，不播放音频。`asr-test` 使用最多 30 秒的 16 kHz 单声道 16 位小端裸 PCM，只回传输入结束前的识别更新数和最终结果数，不启动宿主麦克风。测试内置断句识别时，先选择对应引擎，再以 `{"id":"service.speech-recognition","segmented":true}` 自检，要求至少一个最终结果且没有逐字更新。状态包含媒体 ID/语音状态等必要信息，操作日志最多 200 条、仅驻内存；不是完整 logcat 或性能采样器。

历史 API6 手机验证：同签名管理器绑定、状态读取、签名检查、六包 API6 安装/更新、旧停用状态保留、系统 TTS PCM 自检及 FD 媒体呈现已完成。未依赖测试 APK。持续后台监控和厂商长时间保活仍需实际使用验证。

以下为历史 API7/0.6.0 阶段的更新记录，非当前包版本。2026-10-12 已通过管理器升级手机上的六个引擎/服务包，核验原发布者签名，启停状态保留；已启用插件均加载成功。语音合成服务 PCM 自检完成（182,272 字节，不播放声音）。上述 API6 手机结果仍是历史记录；可选快捷工具示例本次未额外安装。

2026-10-12 手机已更新 API8 Release 和六个 0.7.0/API8 引擎/服务包，发布者、原启停状态和模型保留；已启用插件均加载成功，Piper 与系统保底 PCM 自检、复用及空闲策略通过。统一合成生命周期见 [接口说明](tts-lifecycle.md)。合成服务 0.7.3、识别服务 0.7.1 为页面整理版，API8 不变；管理器桥仍为1，无需重装管理器或测试 APK。

## 当前 API13 宿主与兼容插件

宿主 SDK 为 API13，兼容已验签的 API9–12 包，管理桥仍为 1。云端助手为0.9.6/API13、商店为0.9.3/API12，Lua编辑器为0.9.1/API11；原有引擎、语音服务与原生构建可继续使用 0.8.0/API9，无需为升级宿主重新安装模型。工作进程按已验签清单绑定描述符版本，兼容旧 Kotlin 默认构造器，同时校验 ID、版本、许可及支持范围。主程序有 12 个外置工作进程槽；Lua、云端助手、商店与原生构建均为独立可选包。系统 TTS 已并入语音合成服务作为内置保底，不再分发独立系统包；自检应调用 service.speech-synthesis。

主页直接进入设置，插件按 SettingsEntry 归入音频、内容、扩展或自定义分类；管理器的 navigate 仍通过正常页面栈打开对应页面。商店下载后的原生包继续走宿主验签与审核。Agent 可在原生批准或完全访问授权下安装、启用和调试 Lua；授权不能由插件转发参数自行赋予。原生与 Lua 模板、源目录 Schema 位于独立私有仓库 [KIGTTS-PLUGIN-TEMPLATES](https://github.com/LHT02/KIGTTS-PLUGIN-TEMPLATES)。

当前构建、升级安装、设备状态及功能验收见 [Release 验证记录](release-testing.md)。前面的 API6/7/8 手机结果保留为历史，不替代本轮验证；本页不复制不断变化的测试总数。

## 真实浏览器验收

API11主程序提供签名管理桥的只读 `browser-capture` 命令：`./tools/Invoke-PluginManager.ps1 -Command browser-capture -Payload '{"id":"tools.cloud-agent","mediaId":"实际网页ID","width":960,"height":1280}'`。指定mediaId或本插件Browser pageId其一，复用正式BrowserCapture管线；输出PNG仅位于调用插件workspace/screenshots并返回真实路径和诊断，不申请新的网络授权。管理器APK和桥版本不变。读取后仅清理此次导入的测试媒体与截图，不碰用户素材。

API12增加`browser-session`，传`id`和`operation=open/state/click/type/scroll/wait/screenshot/close`，open指定mediaId/pageId，后续使用返回sessionId。坐标与截图像素一致；权限走正式宿主broker，不自动批准。适用于实际页面运行验收；不是其它应用的截图或点击入口。

API13增加browser-session的open-subtitle操作，返回包含安全区的模拟大字幕布局，使用与普通会话相同的控制与截图流程；模拟不包含用户实时字幕或私有快捷文本。

浏览器截图和会话运行在应用私有显示区，不需要主界面保持前台，也不会接管手机触摸或输入焦点。操作仍走插件权限；会话空闲 10 分钟后回收，进程被系统终止后需重新打开。测试 HTML 预览可用 `navigate` 的 `page=html-preview` 与已导入的 `mediaId`。

语音识别服务已内置。旧服务包保留原设置但不参与加载；可独立更新模型和引擎插件。未选择就绪引擎时不提供聆听与语音输入入口。

2026-10-23：运行时工作进程槽由12扩至100；这里只预先声明可分配服务，实际绑定已启用插件时才启动进程，模型仍按需加载。100槽Manifest/服务检查已通过；实际内存与后台进程数量受Android系统限制，不代表100个大型模型能同时常驻。前述12槽是历史版本记录。
