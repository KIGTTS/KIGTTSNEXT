# 插件模板

先在 KIGTTS 的实验性功能启用「插件编辑器」，再导入 Lua 或 Java 源码 ZIP。保存项目并构建调试版后，从设置进入「插件模板 · Lua」或「插件模板 · Java」。首次构建 Java 项目需要官方编译资源包。目录以 SDK API 1 为基线。

内置预设可在「新建插件」时选择「插件模板」。项目使用自己的包名，作者和签名在「包信息」里维护。AI 助手可以查阅同版本的预设与规范，并打开源码审核。

| 页面 | 手机上能试什么 | Lua | 原生 Java |
| --- | --- | --- | --- |
| 设置与控件 | 相邻圆角卡片、开关、分段、下拉、滑条、输入、折叠、手势和控制面板 | [catalog.lua](../templates/lua-smoke/assets/lua/smoke/catalog.lua) | [CatalogPages.java](../templates/native-java-smoke/sources/com/example/kigtts/smoke/CatalogPages.java) |
| 列表与搜索 | 图标与图片行、详情、行开关、搜索和空状态 | `catalog.lua` 中的列表和搜索页 | `CatalogPages.list` |
| 弹窗与反馈 | 短提示、信息、输入、选单和确认 | [catalog_actions.lua](../templates/lua-smoke/assets/lua/smoke/catalog_actions.lua) | [CatalogAction.java](../templates/native-java-smoke/sources/com/example/kigtts/smoke/CatalogAction.java) |
| 图像与文件 | Markdown、图廊、只读源码、导入导出、相机与拍立得选择回调 | `main.lua`、`catalog.lua`、`catalog_actions.lua` | `SmokePlugin`、`CatalogPages`、`CatalogAction` |
| 聊天界面 | 原生输入、消息、选择芯片与本机回显 | `catalog.lua` 的聊天页 | `CatalogPages.chat` |
| 宿主与字幕 | 应用状态、快捷文本与图库事务、悬浮窗、通知、下载、LED、临时主题、顶底栏和聆听面板 | [checks.lua](../templates/lua-smoke/assets/lua/smoke/checks.lua)、`main.lua` | [SmokeChecks.java](../templates/native-java-smoke/sources/com/example/kigtts/smoke/SmokeChecks.java)、`SmokePlugin` |
| 快捷文本网页 | 离线主题组件、Canvas 2D、WebGL 3D，确认后加入为 PAGE 条目 | `catalog_actions.lua` 的 `add_web` | [QuickWebExample.java](../templates/native-java-smoke/sources/com/example/kigtts/smoke/QuickWebExample.java) |
| 语音接口 | 语音服务与语音包声明、PCM 合成、批量和分段识别 | [speech.lua](../templates/lua-smoke/assets/lua/smoke/speech.lua) | [SmokeAudio.java](../templates/native-java-smoke/sources/com/example/kigtts/smoke/SmokeAudio.java) |

## 原生界面规范

- 新入口使用 `SettingsEntry`。详情页可用 `PluginPage.header` 传入图标、名称和版本。管理列表的图片使用自己目录的 `PluginResource`。Material 图标通过 `symbol` 传递，需要无图标条目时用 `PluginIcon.NONE`。
- 相关表单控件放进 `PageBlock.Group`，宿主负责双层卡片、相邻圆角、海拔色、字体、主题和触感。少量互斥项用 `Choice`，需要下拉时用 `Select`。
- 页面开关回调读 `checked`，滑条和输入读 `value`。控制面板用 `PluginOutcome.Controls`，返回 `NoAction` 保留面板，返回新 `Controls` 替换值。页面值变化用 `RefreshUi`，后台状态变化用 `uiChanges` 发布失效。页面读取保持无副作用，耗时工作放在动作回调。
- 条目和消息保持稳定 ID。可排序列表传 `reorderAction`，回调在 `order` 中收到 JSON ID 数组，校验它完整覆盖原集合后保存。商店式列表使用 `CATALOG`、`ManagementFilter`、`ManagementProgressAction` 与 `ManagementLoadMore`，翻页成功后更换加载动作 token。
- 用 `Message` 给短反馈，需确认时用 `Confirm`。导入、拍照、选择和分享处理取消。正式聊天用独立的生成、完成、失败与取消状态，给用户提供停止动作。
- 包内的 `assets/i18n` 处理实际 `context.locale`。测试长文本、横竖屏、大字体、键盘及亮暗主题下的对比度。原生 UI 由宿主的 Compose 渲染。

## 接口与权限

| 接口 | 参考和边界 |
| --- | --- |
| `AppBroker` | 按需声明 `app-control`，先 Describe 再调用已公布能力。导航、字幕、朗读、公开设置和跨插件动作均由宿主处理。跨插件调用使用目标公开的 `automationActions` |
| `QuickTextBroker` | `quick-text-read/write` 分别授权。Query → Prepare → Commit，冲突重新查询版本，超时先查回执。宿主要求审核时用 REVIEW |
| `StickerBroker` | `stickers-read/write` 分别授权。使用图库事务处理相册、拍立得和贴纸。照片、二维码、边框等见[图库合同](contracts.md) |
| 资源、网络、通知和悬浮窗 | 文件保存在插件自己的目录，资源包安装交给宿主校验。网络、通知和悬浮窗按实际行为申请能力。试验通知使用自己的 ID，并提供取消 |
| 主界面与字幕 | `UiAction`、`GestureAction`、LED、`SetHomeChrome`、`SetListeningPanel`。临时主题配套恢复动作，持久主题使用独立的 `ChangeSavedAppearance` |
| 合成、识别和语音包 | 声明服务角色与实际流式能力。示例使用固定 PCM 和回调文本验证传输。生产引擎可在同一接口接入真实模型 |

复制模板做独立插件时，更新 ID、移动原生入口类、使用自己的发布者密钥和网页示例 UUID，并从清单移除用不到的能力。协议细节见 [SDK 与宿主合同](contracts.md)、[Lua](lua.md) 和 [网页 UI 规范](quick-text-web.md)。

编写页面前先看[插件界面规范](plugin-ui.md)。
