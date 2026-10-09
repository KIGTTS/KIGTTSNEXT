---
name: kigtts-plugin-debug
description: 使用 KIGTTS 独立插件管理器检查、安装和调试专用插件包及媒体，减少测试 APK 重装；不用于一般 Android 开发或 Codex 插件管理。
---

# KIGTTS 插件调试

在本仓库运行 PowerShell 7。先阅读 [管理器协议与操作说明](../../../docs/plugin-manager.md)，使用 [Invoke-PluginManager.ps1](../../../tools/Invoke-PluginManager.ps1)，避免重写跨 UID 文件传输或 ADB JSON 调用。

管理器包名是 `com.kigtts.manager`，主程序是 `com.kigtts.next`。管理协议单独版本化，管理器不依赖插件 SDK。两 APK 需要同一签名；插件的发布者签名另行验证。检查现有安装与 `snapshot`，仅在缺少管理桥或 APK 自身修改时更新相应 APK。普通插件代码更新应重建 `.kigplugin` 并用管理器安装，不重新安装测试 APK。

对要安装的包先运行 `inspect`，核对 ID、API、版本和发布者，用明确的摘要作为 `install` 的 publisher；已有包升级保留启停状态和 Appdata。主程序与管理桥共享运行时。资源导入走 `resource`，外置文件不能写到其它插件目录。

`execute` 返回动作结果供检查，不自动应用其中的宿主导航或媒体命令。`tts-test` 仅检查真实 PCM，不播放声音。需要实际媒体画面时用 `present` 的文件与 MIME，然后记录媒体 ID，结束只清理本次导入项；不要删除用户的现有条目、语音包或配置。

CLI 是作业队列。超时可能已经成功：先查询输出的作业 ID 或现有状态，不能自动重复安装、导入或卸载。管理器进程重启后作业缺失也不能据此认定失败。手机明确返回安装取消时，说明被取消的具体 APK；用户允许后再重试，不更改手机安全策略。

厂商可能阻止绑定未启动的主程序，先打开 KIGTTS 检查；保留用户的后台/不保留活动设置。主程序正在显示时，CLI 不应将管理器切到前台，以免中断媒体或销毁测试 Activity。手机没连接、渲染/手势未实际验证时，区分构建/本地回归与真实设备结果。

文件使用 UTF-8 无 BOM。代码或文档有修改时按仓库约定保存本地 PROJECT；遵循本次任务已有的 Git 提交与安装授权。