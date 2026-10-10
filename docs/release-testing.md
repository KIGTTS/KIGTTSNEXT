# Release 构建与性能采样

主程序 Release 启用 R8、资源收缩及 AGP 8.13 的一体化资源收缩。正式构建输出未签名 APK，不在生产构建中配置开发证书。`app/proguard-rules.pro` 保留插件 API、Android 插件环境及外置 DEX 共用的 Kotlin/协程/序列化运行时 ABI；安装在之后的插件不在 R8 的静态调用图中，不能仅保留入口类。

## 本地测试包

使用 PowerShell 7、仓库 Gradle 8.13 与 JDK 17：

```powershell
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-17.0.17.10-hotspot'
./.tools/gradle-8.13/bin/gradle.bat --gradle-user-home .gradle-user-home :app:assembleDebug :app:assembleRelease :app:lintRelease
./tools/Build-ReleaseTest.ps1 -SkipBuild
./tools/Measure-ApkSize.ps1 -Apk @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/release-test/KIGTTSNEXT-release-localtest.apk')
```

签名脚本生成 `app/build/outputs/apk/release-test/KIGTTSNEXT-release-localtest.apk`，使用本机已有默认开发证书，仅用于覆盖本机原有同签名安装。脚本检查签名与 Debug 相同、16 KiB 对齐；该 APK 仍为非 debuggable 的压缩 Release，生产签名配置保持独立。ZIP 分类统计不含签名块与对齐空隙，因此分类之和不等于 APK 文件大小。

## 设备采样

安装同版 Debug 或本地测试 Release 后运行：

```powershell
./tools/Measure-AppPerformance.ps1 -Device cfc8ef16 -Label debug-final -SkipScroll
./tools/Measure-AppPerformance.ps1 -Device cfc8ef16 -Label release-final -SkipScroll
```

两个命令须分别在对应 APK 已安装后执行。结果写入忽略的 `.tools/performance/<标签>/<时间>/`，包含已安装 APK 的实际 SHA256、环境、原始 `am start -W`/`dumpsys gfxinfo`/内存数据及 `summary.json`。

每种构建预热一次，随后测五次进程冷启动，中位数取 `TotalTime`。先回桌面、临时结束管理器进程并结束主进程，确认 PID 消失后启动；仅 `LaunchState=COLD` 的样本有效，最后恢复管理器和主程序。页面采样经管理器驱动四轮“设置 → 外观 → 返回 → 返回 → 插件 → 返回”，保留真实页面动画；分别记录主进程及各插件进程 PSS/RSS。

不修改刷新率、动画倍率、插件启停、模型、媒体或系统“不保留活动”设置。进程冷启动不代表重启后的存储冷缓存；导航 HWUI 数据不等同触控到显示的延迟或视频解码帧率，单次 PSS 不是长期内存峰值。可选设置滚动必须通过截图像素变化证明实际发生；当前手机 ADB 滑动未证实移动，最终比较应使用 `-SkipScroll` 并明确排除滚动结果。

## 本轮状态（2026-10-10）

| 构建 | APK 文件大小 | SHA256 |
| --- | ---: | --- |
| Debug | 30,938,034 字节 / 29.505 MiB | `EAAAE0B0FBFBBC8D436A838C95D8D6EEE9B0147A258E56B3C97FBA22860A3934` |
| 本地签名 Release | 3,523,964 字节 / 3.361 MiB | `B783D1FC45F467C0A0B3CE89FF2DE28AFC982E18D6BF278FBC723C95C740AE82` |

Release 文件大小减少 88.6%。压缩内容主要为一个 DEX（3,223,569 字节），资源 151,403 字节，四架构 Compose 图形路径库合计 37,392 字节，基线配置文件 7,445 字节；不包含语音引擎、模型或 `.kigplugin` 包。Release 签名、对齐、非 debuggable 检查通过；本轮主程序 184 项回归通过，包含横屏布局/整列吸附/连续拖动/旋转/全屏输入的 10 项新增检查。最后的受限窗口列数恢复修正后，横屏 10 项再次通过。Release Lint 0 错误/31 警告/1 提示。

按钮修复后的首版非 debuggable Release 已覆盖安装成功，旧插件启停状态保留且已启用插件加载无错误。后续视频/拖拽/旋转修复，以及全屏条目容器变换、抽屉与条目尺寸调整、横屏左栏/左右字幕/右侧整列快捷面板完成本地回归；手机随后断开 ADB，最终新版安装、同版 Debug/Release 性能比较与视频窗体变化的 GPU 检查等待重连。迁移前采样和未实际移动的滑动数据不用于最终性能结论。

依据：[Android 构建优化](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)、[dumpsys 性能指标](https://developer.android.com/tools/dumpsys)。
