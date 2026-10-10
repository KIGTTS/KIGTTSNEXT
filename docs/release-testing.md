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
| Debug | 30,938,113 字节 / 29.505 MiB | `53EE3E7C2575BFC43106E383E88379FC2D89B0535BC10F0220065A2658A51B1E` |
| 本地签名 Release | 3,544,523 字节 / 3.380 MiB | `6EDDD5A4256D123111AA286139427C84522A8F4C80E21C5F054A84F789492470` |

Release 文件大小减少 88.5%。压缩内容主要为一个 DEX（3,249,511 字节），资源 151,403 字节，四架构 Compose 图形路径库合计 37,392 字节，基线配置文件 7,537 字节及 smartcrop MIT 许可639字节；不包含语音引擎、模型或 `.kigplugin` 包。Release 签名、16K对齐、非 debuggable 检查通过；本轮主程序196项完整回归通过，包含原横屏10项和新增算法/取景/缓存/网页安全区12项。Release Lint 0错误/36警告/1提示（新增五项为KTX写法建议）。

此前横屏 Release（SHA256 `B783D1FC45F467C0A0B3CE89FF2DE28AFC982E18D6BF278FBC723C95C740AE82`）已覆盖安装并正常冷启动，回读手机APK确认一致且非debuggable。最新媒体封面/半透渐变模糊/网页整卡片安全区的上表构建尚未安装：手机在本轮真机检查前断开，ADB无设备，也未导入测试素材。原生Chromium安全区、设备视频封面、同版Debug/Release性能比较和视频窗体变化GPU检查仍待真机核验。测试APK继续按用户选择暂缓；单次安装启动不作为性能比较结果，迁移前和未实际移动的滑动数据不采纳。

依据：[Android 构建优化](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)、[dumpsys 性能指标](https://developer.android.com/tools/dumpsys)。
