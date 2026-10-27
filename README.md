# KIGTTS NEXT

目前仍处于研发阶段，后续更新可能包含不兼容变更。更新前请备份重要内容。

KIGTTS 的 Android 原生重构续作，采用 Kotlin、Jetpack Compose 和 Material 3 Expressive。主程序、通用 SDK 与运行时采用 MIT；原版移植的合成/识别实现保留各自 GPLv3 与第三方许可证，详见插件 LICENSE/NOTICE。

## 当前实现

- 主页支持大字幕、可调整的聆听面板和快捷文本面板；横屏使用左侧栏、左侧聆听字幕与右侧可变列数快捷栏。输入、排序、缩放、面板吸附及预测返回共用原生动效与触感反馈。
- 快捷卡片共有字幕和可选缩略标题，可附加音频、图片/实况照片、视频或 HTML 页面。内置富文本编辑、媒体预览、内容感知缩略图、HTML 项目文件管理与带行号的源码编辑器。
- 拍立得支持富士 mini／SQUARE／WIDE 三款相纸布局和纯白纸边，经典默认框右下的 KIGTTS Logo 与 PNG 导出一致，品牌框不强制添加。相册支持三层平面堆叠翻页、2／3／4 列宫格及继续缩小进入相册总览；反向放大恢复。透明全屏预览可缩放整张带边框照片，裁剪仅在编辑页进行。浏览区域或标题下滑进入原生相机、上滑返回；新照片经主题化 KIGTTS 打印顶栏送出，同一张相纸连续展开为预览，画面就绪后底层返回相册。相机提供辅助线、水平仪、闪光灯、倒计时和五款滤镜，原始 JPEG 与滤镜图分开保存。裁剪与贴纸分为两个 Tab，支持双指构图、贴纸旋转/缩放/镜像、Unicode 二维码及品牌边框资源；贴纸库与快捷文本图片可互转，面对面发现仅预留开关。
- 相机支持真实预录的 Android 实况照片，选图入口也放在相机内；只有预览在相纸下显示播放控件，翻页保持静态。分享可以合成带纸框与贴纸的实况，转快捷文本前可分别选择保留相框和贴纸。照片名称默认留空，接收照片持久设为只读；相册栏默认折叠，展开后收起大标题。编辑工具内容独立滚动，顶部手柄控制面板折叠；相册支持多选、排序与批量移动。原生界面不提供空白照片创建，插件保留正式事务入口。
- 相机实时滤镜通过 CameraX 预览 GPU 处理，切换强度连续更新同一颜色矩阵；镜头切换和刷新用缓存的模糊画面淡入淡出。相册浏览和已解码照片跨横竖屏保持。
- [画板](docs/sketch.md)全屏绘画并保留原始画布方向，支持压感、临时橡皮擦、双指缩放和平移；横屏工具栏位于右侧。颜色直接使用共享 HSV 取色器，作品同时存入独立画板相册与系统图库，可重新打开矢量笔迹。
- 设置采用双层分组卡片，统一主题、动态取色、亮/深色、三档对比度、三档圆角和触感反馈。主页设置键直接进入设置；插件入口按分类并入同一页面，不再使用抽屉菜单。
- [个人信息](docs/personal-profiles.md)支持单张个人名片、标签和可调整尺寸的链接/社媒/文字/图片磁贴（含 GIF）；面对面收到的名片存入纵向展开的扩列收藏夹，扫码网页沿用主程序的主题和布局。
- 语音合成服务管理声音与跨引擎语音包，**内置系统 TTS 保底**，不再分发独立系统 TTS 包。Piper 在独立插件中复用模型/发音转换器并分段输出；预热、保持加载和两分钟空闲释放也由插件实现。
- 语音识别服务统一提供聆听字幕、浮窗和语音输入，调用独立的增量流式/SenseVoice 引擎。连续结果自动分句、固化前文并修订尾段；长按语音输入使用覆盖主界面的滑动释放区，可发送、取消、持续输入或单次朗读。空白提示也遵循大字幕字号设置。
- 可选外置插件提供云端 AI 助手、多会话流式聊天、Lua 插件编辑器、带依赖解析的插件商店和 Java/Kotlin 原生构建。主程序提供标准页面与授权接口，不包含这些实现或编译器。
- 独立插件管理器 APK 可反复安装、更新、调试与监控专用包，减少测试 APK 重装。

Material 组件固定使用官方 `material3:1.5.0-alpha01`，该版本包含 Expressive 预览 API。当前插件 API 为 **30**，兼容已验签API9–30包。云端助手、商店和编辑器均为独立可选包；引擎与语音服务可保留 **0.8.0/API9**。API8 及更早的二进制需同发布者升级。运行时提供 100 个外置插件工作进程槽，各插件资源统一归入 `appdata/Kigtts/plugin/plugindata/<pluginId>/`。独立进程隔离崩溃，原生插件仍以本应用 UID 运行，应安装可信来源。

## 开发文档

| 主题 | 文档 |
| --- | --- |
| 插件 SDK、设置入口、页面与宿主操作 | [API31](docs/plugin-api.md)、[页面风格](docs/plugin-page-style.md)、[快捷文本接口](docs/quick-text-plugin-api.md) |
| 专用包、资源归属、安装与调试 | [插件包](docs/plugin-packages.md)、[独立管理器](docs/plugin-manager.md) |
| 合成与识别 | [合成生命周期](docs/tts-lifecycle.md)、[识别服务](docs/recognition-service.md)、[流式识别包](docs/streaming-recognition.md) |
| 媒体、卡片与源码 | [媒体协议](docs/media-content.md)、[快捷卡片](docs/quick-cards.md)、[HTML 文件与编辑器](docs/html-editor.md) |
| 拍立得、贴纸与界面语言 | [浏览与相册](docs/polaroid-browsing.md)、[实况预览与分享](docs/polaroid-motion.md)、[实况采集](docs/camera-motion-capture.md)、[实时滤镜](docs/camera-live-filters.md)、[贴纸与拍立得接口](docs/stickers-api.md)、[内建相机](app/src/main/java/com/kigtts/next/ui/cards/camera/README.md)、[本地化预留](docs/ui-localization.md)、[空白提示](docs/subtitle-placeholder.md) |
| 可选工具 | [云端助手](docs/cloud-agent.md)、[Lua 编辑器](docs/lua-plugins.md)、[原生构建](docs/native-builder.md)、[插件商店](docs/package-store.md) |
| 构建、包体与实测记录 | [Release 验证记录](docs/release-testing.md) |

可直接导入的 Kotlin、Java、Lua、多文件工具和 HTML 计算器模板，以及源目录 Schema/签名辅助脚本，放在独立私有仓库 [KIGTTS-PLUGIN-TEMPLATES](https://github.com/LHT02/KIGTTS-PLUGIN-TEMPLATES)。SDK 尚未发布 Maven 坐标，原生插件通过独立的 [plugin-sdk](https://github.com/KIGTTS/plugin-sdk) 固定子模块编译。

## 构建

需要 JDK 17、Android SDK 36 与 PowerShell 7；本机 `local.properties` 设置 `sdk.dir`。源文件使用 UTF-8 无 BOM，每次修改同步保存本地 PROJECT 记录。

```powershell
git submodule update --init .sdk
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:assembleRelease
./gradlew.bat :plugin-api:test :plugin-runtime:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
```

主 APK 位于 `app/build/outputs/apk/`，外置包位于 `build/plugin-packages/`。外置插件在各自仓库独立构建和签名；`Build-EnginePlugins.ps1 -PluginSourceRoot <独立仓库目录> -SigningKey <发布者私钥> -Modules <模块名>` 仅为兼容入口，不再编译本仓库模块。`quick-text-tools` 是可选接口示例，不在默认包集合中。主 APK 不携带引擎类、模型、编译器或 `.kigplugin` 资产。

Release 启用 R8 与资源收缩。当前构建、签名检查、安装结果和手机采样统一记录在 [Release 验证记录](docs/release-testing.md)；其它文档中的 API2/API3/API8 测量均标注为历史结果，不代表 API9 新功能已经完成真机验收。

## 独立仓库

主程序仓库为 [KIGTTS/KIGTTSNEXT](https://github.com/KIGTTS/KIGTTSNEXT)，保持私有。插件与SDK统一由 [KIGTTS组织](https://github.com/KIGTTS) 管理，一个插件一个仓库，保留各自LICENSE/NOTICE。主程序只固定SDK子模块，不包含外置插件源码。完整映射、许可证和跨仓库测试见[仓库结构](docs/repositories.md)。

聊天文件接口与验证说明见 [API31](docs/api31-chat-files.md)。
