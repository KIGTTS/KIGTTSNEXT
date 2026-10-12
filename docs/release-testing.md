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

## 已安装基线（2026-10-10）

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

## 界面动效版本（2026-10-11）

| 构建 | APK 文件大小 | SHA256 |
| --- | ---: | --- |
| Debug | 30,588,458 字节 / 29.171 MiB | `8A69808887993025BCDB9163A51C580A1FEE08EDC99F829787B49167ACB40870` |
| 本地签名 Release | 3,589,579 字节 / 3.423 MiB | `31C4D0FA27544912B2CB6AA350D08BFAD0CFC5DB7285E87F76E54CDA05F11F43` |

主程序 234 项完整测试全部通过（65 套件，0 失败、错误或跳过）。Native Graphics 新回归检查普通二级编辑页面和预测返回、排序拾起/换位/释放触感、三行以上逐行停靠、真实列吸附触感、返回键旁加载环、动态按钮槽宽及退场禁用、字号栏出入中间帧、短按/取消/连续按压、横屏指针仍按下时恢复折叠尺寸、活动 Tab 胶囊及切组自动滚入可见区域，以及缩略图初次淡入/新旧图交接/加载失败保留旧图。

Debug/Release/R8 及两种 Lint 完成，Lint 各 0 错误、37 警告、1 提示。合并构建时测试子进程曾退出，之后分离运行完整测试与构建均成功；没有将不完整报告计为通过。Release 同签名及 16K 对齐检查通过，覆盖安装到 Xiaomi 23113RKC6C，回读 APK 摘要与上表一致，包标记不含 DEBUGGABLE。管理桥 API6 正常；九个插件版本和启停状态保留，八个启用项均已加载且无错误。仅更新主程序，未安装测试 APK，也未更改模型、素材或系统设置。

本次未重复媒体导入和性能采样；前节启动耗时/HWUI 数值属于 2026-10-10 的 APK。Native Graphics 的动画与触感事件断言不代表真机触控时延或硬件振动验收。

## 两行与可用态版本（2026-10-12，已安装）

| 构建 | APK 文件大小 | SHA256 |
| --- | ---: | --- |
| Debug | 30,588,458 字节 / 29.171 MiB | `F2B9BE57D447AF5C327CE27BA16969BB322478BBF3D6CFDEE4DC73C4AE1294B8` |
| 本地签名 Release | 3,589,579 字节 / 3.423 MiB | `FC5A9E811172390E71FF7DE869F7DE0A649D893F9FE8C9DE53DEE2EAEF79FB06` |

237 项完整回归通过（67 套件，0 失败、错误或跳过）。竖屏移除旧半行紧凑停靠，改为完整两行，受限窗口不再生成介于两行和三行的停靠。通用动作按钮、开关、滑条、菜单和输入框平滑切换可用态颜色，真实禁用即时生效。光标移动条统一用 220ms 相位驱动淡入出、本体宽度和两侧空间，保持底栏高度与原相对字素移动/震动；退场移除手势与无障碍动作，快速开关能复原。

新 Native Graphics 回归检查颜色混合帧、按住时禁用/取消、禁用期间点击不执行、连续反转归位、输入恢复，以及光标入退场中间像素、稳定高度和完整移除。缩略图回归等待实际首个像素变化，再要求混色与不透明交接，避免缓存准备完成但 UI 尚未采纳时误读旧图。

Debug/Release/R8 与两种 Lint 通过（各 0 错误、37 警告、1 提示）。同证书、16K 对齐 Release 已覆盖安装，回读手机 APK 摘要与上表一致且不含 DEBUGGABLE；九个插件版本与启停状态保留，八个启用项加载无错。仅更新主程序，未安装测试 APK。本次没有重新采样媒体/GPU 性能；前述性能数据仍属于 2026-10-10 基线。

## 媒体播放与编辑页面（2026-10-12，待安装）

| 构建 | APK 文件大小 | SHA256 |
| --- | ---: | --- |
| Debug | 30,588,458 字节 / 29.171 MiB | `2C778E007379DB4D5E676EFD34AF5626C9028D3BD8B9360F4705272DDA453E94` |
| 本地签名 Release | 3,605,963 字节 / 3.439 MiB | `8C954A2E8B453A6659642A301454F186316A74362C02B72FECC5F15483E8B2D9` |

248 项完整回归通过（71 套件，0 失败、错误或跳过）。新增 Native Player 夹具与真实 Compose 手势检查首次激活的实况自动播放、画面点击暂停/继续、完成后手动重播、声音策略保存/历史/重启、拖动每次移动立即 seek 且暂停、松手恢复原状态与离页取消恢复、闲置控件收细/暂停恢复及收细后右端拖动。编辑页按共有内容、类型和可折叠外观组织，预览控制条位于视频画面之外；声明式插件内容和嵌套动作自动过渡，更新保持动作身份，移除当帧禁用，快速反转复原。

Debug/Release/R8 和两种 Lint 通过（各 0 错误、37 警告、1 提示）。签名与 Debug 同证书，16K 对齐及非 debuggable 检查通过，Release 比同版 Debug 小 88.2%，比上一个安装包增加 16 KiB。APK 已准备在本地；用户取走手机并要求明天再装，因此此次没有安装主程序或测试 APK，也没有设备性能、真实媒体精确 seek 时延或插件运行状态的新测量。前面的安装与性能记录属于各自明确的 APK，不能视为本版真机结果。

## 共有格式与媒体标识（2026-10-12，待安装）

| 构建 | APK 文件大小 | SHA256 |
| --- | ---: | --- |
| Debug | 30,588,458 字节 / 29.171 MiB | `277456B526E8CD97843A2B420F15FB78A9BE26E0FDAE5BA944781143A84C9FEE` |
| 本地签名 Release | 3,605,963 字节 / 3.439 MiB | `A8E347B81FA2EB6FF1486DAF913505E7F033860DB81AD8A668CAC5DB13DC67FD` |

250 项完整回归通过（71 套件，0 失败、错误或跳过）。富文本和文本编辑器在共有区常驻，四个同排图标 Tab 为无/音频/媒体/页面；新增下划线与小/正常/大字号，保存和重启兼容旧格式与旧大字号。音视频展示使用类型或用户填写的正文/标题，预览、播放、管理不显示原文件路径和文件名。实况/音频播放胶囊显示 Motion Photos On/Audio File 图标，展开播放/暂停图标交叉淡变；尺寸、按压与控制条过渡保持。

Debug/Release/R8 与双 Lint 通过（各 0 错误、37 警告、1 提示）。同证书签名、16K 对齐及非 debuggable 核验通过；文件体积相同但摘要已更新。本版本仍未安装，手机验收按用户安排延后。

依据：[Android 构建优化](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)、[dumpsys 性能指标](https://developer.android.com/tools/dumpsys)。

## 2026-10-12：HTML 项目与原生页面控件（未安装）

| 包 | 大小 | SHA-256 |
| --- | --- | --- |
| Debug | 30,588,458 字节 / 29.171 MiB | `48BCEF0E8E6087EE378B68C4AAAB5FE13376F27E1DECB6A8B43A14DF8296DE89` |
| 本地签名 Release | 3,655,115 字节 / 3.486 MiB | `85796EE61BBD85E0DC7C15EA73644B07983E69AF377BC11EAF5F561AF76181D0` |

263 项主程序完整回归通过（75 套件，0 失败、错误或跳过）。HTML 文件管理支持目录、批量文件/目录导入、复制/移动/重命名/确认删除与主页面选择；独立源码页面支持行号、键盘上方可滚动/展开的快捷栏、选区命令、缓存草稿恢复与异步保存防重入。类型 Tab 改为原生图标在上、文字在下，背景继承卡片；插件瞬态锁定保留配色且阻止操作，原生 animated shapes 使用即时按压。加载环在展开顶栏位于返回键旁，单行顶栏位于标题后。

Debug、Release、R8 与 Debug/Release Lint 均通过（各 0 错误、37 警告、1 提示）；本地签名和已有包一致、16K 对齐、非 debuggable 检查通过。用户已取走手机，本次不安装；该构建没有新增设备性能采样。之前的启动/帧率/内存数据仍仅代表各历史已安装版本。