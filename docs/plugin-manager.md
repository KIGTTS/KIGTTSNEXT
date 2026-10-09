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
./tools/Invoke-PluginManager.ps1 -Command inspect -File build/plugin-packages/engine.piper-0.5.0.kigplugin
./tools/Invoke-PluginManager.ps1 -Command install -File build/plugin-packages/engine.piper-0.5.0.kigplugin -Payload '{"publisher":"检查结果中的签名摘要"}'
./tools/Invoke-PluginManager.ps1 -Command restart -Payload '{"id":"engine.piper"}'
./tools/Invoke-PluginManager.ps1 -Command tts-test -Payload '{"id":"engine.system-tts"}'
./tools/Invoke-PluginManager.ps1 -Command execute -Payload '{"id":"debug.ui","action":"动作ID","inputs":{}}'
```

支持 `-Device` 选择设备。`resource` 传 `id/directory/name/zip` 并带 `-File`，文件由宿主原资源导入器限定到指定插件目录。`present` 传 `mime` 并带图片、动态照片、视频或 HTML 文件，打开主程序呈现，用于真实媒体检查；`select-media`/`remove-media` 传媒体 `id`，测试结束应仅移除此次导入项。

命令先入队并返回作业 ID，再查询结果；JSON 使用 UTF-8 Base64，避免 shell 解释内容。操作完成后 CLI 清除自己的传输文件。超时表示结果未知，保留文件和作业 ID，须先检查结果/状态；不能直接再次提交安装或卸载。作业记录最多 50 条、仅在管理器进程中保留，进程退出后缺失也不能据此断言操作未完成。

## 管理协议 1

`:manager-protocol` 是独立于插件 API6 的稳定 AIDL 接口，只传版本化 JSON 和可选 FD。主程序服务同时验证 `signature` 权限、调用者签名以及指定包名；同签名的其它应用也不被接受。请求 JSON ≤64 KiB，普通传入文件 ≤768 MiB，插件资源 ≤2 GiB。动作调试和合成自检限时 30 秒，异常作为结果返回，临时宿主文件最终回收。

`execute` 返回原始插件动作结果供检查，不自动执行其网页/导航等宿主命令。`tts-test` 检查有效 PCM 及完成事件，不播放音频。状态包含媒体 ID/语音状态等必要信息，操作日志最多 200 条、仅驻内存；不是完整 logcat 或性能采样器。

当前手机验证：同签名管理器绑定、状态读取、签名检查、六包 API6 安装/更新、旧停用状态保留、系统 TTS PCM 自检及 FD 媒体呈现已完成。未依赖测试 APK。持续后台监控和厂商长时间保活仍需实际使用验证。
