# 系统 TTS 插件来源

系统引擎候选回退、异步初始化、synthesizeToFile → PCM 的基本流程移植并重写自 LHT02/KIGTTS 的 `android-app/app/src/main/java/com/kgtts/app/audio/Engines.kt` / `SystemTtsEngine`。

本插件目录按 GPL-3.0-only 提供，许可证见 LICENSE。它不静态链接到 MIT 主程序；通过公开的 API v2 / IPC 通信，单独构建和安装。

重写了可取消的等待与超时、RIFF 分块读取和清理流程，删除宿主缓存路径、原版全局偏好和 UI 依赖。
