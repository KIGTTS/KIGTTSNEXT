# 内置实验工具

「设置 → 实验性功能」提供 AI 助手和插件编辑器的开关。两项独立，首次升级也默认关闭。开启后使用设置、主页或抽屉中原有入口。关闭会停止对应工作进程，保留既有数据和用户授予的权限。对这两项功能的权限管理仍可从助手设置或编辑器进入。

APK 随附同一官方发布者签名的工作包，启用时从本地 APK 安装，不访问商店，也不用联网下载。宿主继续检查包 ID、版本、签名和文件哈希，并使用原有隔离工作进程与权限代理。同一发布者的已有会话、模型密钥和 Lua 项目都沿用原目录。旧的外置包不能通过普通插件管理再次安装、启停或卸载。

| APK 内的资源 | 源码提交 | 包版本 | SHA-256 |
| --- | --- | --- | --- |
| `experimental/assistant.kigplugin` | `KIGTTS/plugin-cloud-agent@ab6dcad` | 0.9.29 | `8ad2bfb6ffe7101a48d46648f8a6e2768ec94eb6b4bbcda149f078bbe965ae57` |
| `experimental/editor.kigplugin` | `KIGTTS/plugin-lua-runtime@aa427dd` | 0.9.4 | `b30ea8b91d94f12237a284b9ddae577e2fee579a2cf710a03a7d626de0b0595c7` |

更新时用 `tools/Build-EnginePlugins.ps1 -Modules cloud-agent,lua-runtime` 从固定源码重新打包，用新的模块版本和签名更新 APK 内资源与 `ExperimentalTool.bundles` 的版本号。两份包内保留 MIT 许可与 Notice，Lua 包同时包含 LuaJ 许可。用本地 APK 验证旧包升级和默认关闭后，再提交更新。
