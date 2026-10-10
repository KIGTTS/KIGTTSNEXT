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
| Debug | 30,588,458 字节 / 29.171 MiB | `8104D6CF2589D01A00169B1C2384799E749D6FD2EFE5D97BECE4061F74D9E4F3` |
| 本地签名 Release | 3,605,963 字节 / 3.439 MiB | `0FDAFEE9617FA55C18CD007B4E3155F7F63A512C59989C9C9F32E34D35C0AB16` |

Release 文件大小减少 88.2%。压缩内容主要为一个 DEX（3,302,257 字节）、资源 148,075 字节、四架构 Compose 图形路径库合计 37,392 字节，以及基线配置文件和 smartcrop MIT 许可合计 8,218 字节（其中许可 639 字节）；不包含语音引擎、模型或 `.kigplugin` 包。Release 签名、16K 对齐、非 debuggable 检查通过；主程序 222 项完整回归通过，Debug/Release/R8 及两种 Lint 完成。两种 Lint 各 0 错误、37 警告、1 提示。

上表 Release 已覆盖安装到 Xiaomi 23113RKC6C（Android API 36），回读手机 APK 的 SHA256 确认一致，包标记不含 DEBUGGABLE。管理桥挂接成功；原有六个外置 API6/0.5.0 插件及三个内置插件的启停状态保留，启用项均正常加载。仅主程序更新，测试 APK 继续按用户选择暂缓。

通过独立管理器导入自建的静音音频、测试视频、Android 动态照片及 HTML。真机确认音频保留已有字幕、播放胶囊居中，删除正在展示的音频不再触发播放器线程错误并保留字幕；视频实际解码和动态照片默认自动播放可见。Chromium 测试页正文高度和视口同为 353 CSS 像素，顶部及左右安全区随真实卡片换算，短期测试 Cookie 可读写。所有本次导入项和临时 Download 文件已清理；仅测试 Cookie 自动到期，未清除用户 Cookies。

本地原生回归验证条目发送填写的字幕、关闭音频保留字幕/选区、编辑页共用字段位于 Tab 上方及两类弹窗的内容尺寸。设备坐标点击未证明实际触发，不能据此声称抽屉/预测返回手势、编辑预览按钮或系统选择器已做真机验收。全部视频窗口变化、长期 GPU 表现和同版 Debug/Release 性能比较仍未覆盖；单次安装启动不作为性能结果。

同一已安装 Release 的五次有效进程冷启动为 305、300、286、251、293 ms，中位数 293 ms；预热不计入，系统“不保留活动”保持开启。四轮管理桥导航采样共 1,054 帧，现代截止时间口径 Janky 24 帧（2.28%），legacy 口径 32.45%；HWUI P50/P90/P95/P99 为 12/24/34/53 ms，GPU P95 为 6 ms。两种 Janky 口径不同，不能直接换算成整个软件的帧率。静止主页主进程 PSS 90.32 MiB，含五个已加载外置插件进程合计 170.72 MiB。未采纳 ADB 滚动，也未测媒体解码帧率或输入到显示延迟。

原始数据保存在本机忽略目录 `.tools/performance/release-cards-final/20261010-171932/`，记录包摘要、系统环境、温度/电池和逐次采样。该结果是当前设备和状态下的基线，不代表与同版 Debug 的速度比较。

依据：[Android 构建优化](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)、[dumpsys 性能指标](https://developer.android.com/tools/dumpsys)。
