# 内置实验工具

「设置 → 实验性功能」提供 AI 助手和插件编辑器的开关。两项独立，首次升级也默认关闭。开启后使用设置、主页或抽屉中原有入口。关闭会停止对应工作进程，保留既有数据和用户授予的权限。对这两项功能的权限管理仍可从助手设置或编辑器进入。

APK 随附同一官方发布者签名的工作包，启用时从本地 APK 安装，不访问商店，也不用联网下载。宿主继续检查包 ID、版本、签名和文件哈希，并使用原有隔离工作进程与权限代理。同一发布者的已有会话、模型密钥和 Lua 项目都沿用原目录。旧的外置包不能通过普通插件管理再次安装、启停或卸载。

| APK 内的资源 | 源码提交 | 包版本 | SHA-256 |
| --- | --- | --- | --- |
| `experimental/assistant.kigplugin` | `KIGTTS/plugin-cloud-agent@8e2d361` | 0.9.32 | `a069233867186bcc7c774dd5d0aab74024b72bc6f12774bb78c36bbab8c4fc62` |
| `experimental/editor.kigplugin` | `KIGTTS/plugin-lua-runtime@75bfca6` | 0.9.6 | `c4d70330c1cf43a9042516e5e3817def676eada122aca786eda137b4aef7d902` |

更新时用 `tools/Build-EnginePlugins.ps1 -Modules cloud-agent,lua-runtime` 从固定源码重新打包，用新的模块版本和签名更新 APK 内资源与 `ExperimentalTool.bundles` 的版本号。两份包内保留 MIT 许可与 Notice，Lua 包同时包含 LuaJ 许可。用本地 APK 验证旧包升级和默认关闭后，再提交更新。
