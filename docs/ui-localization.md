# 界面语言与占位文字

主程序在「设置 → 外观 → 界面语言」提供简体中文、简体中文（轻松版）、English 和 한국어。内置目录位于 `app/src/main/assets/i18n/`；非中文目录使用中性 BCP 47 标签 `en`、`ko`。选择立即生效并持久化。目录已覆盖主要页面标题、设置导航、快捷文本、聆听字幕和语音输入的常用操作、部分相机和外观设置、备份恢复、相册操作及离线名片分享。尚未迁移的界面文案使用简体中文。

`PluginContext.locale` 与 `PluginEnvironment.locale` 传递 BCP 47 界面语言，例如 `zh-CN`、`en-US`。它们不修改识别包或朗读声音所支持的语言；`RecognitionConfig.language` 仍是另一项音频能力。

插件自行随包存储和更新语言文件。主程序仅传递当前语言，不保存或翻译插件词条。插件可覆盖 `KigPlugin.localization`，声明 `PluginLocalization(defaultLocale, supportedLocales)`；此信息进入跨进程 `PluginSnapshot`。页面名称、按钮、设置入口与状态文案由插件依据收到的 `context.locale` 生成。插件缓存语言相关页面时，应将 locale 加进缓存身份，或在语言改变时发送 `uiChanges`。

随 APK 内置的识别和朗读组件各自维护目录，分别位于 `speech-recognition/src/main/resources/localization/recognition/` 和 `reading/src/main/resources/localization/reading/`。引擎服务返回的名称、用户内容和第三方数据保持原样。

不可变资源可放在 `assets/i18n/<locale>.json`，通过 `PluginStrings.fromAssets(environment.assets)` 读取；格式为文案键 → 字符串。`text(key, locale, arguments, fallback)` 依次查询完整语言标签、上级语言、默认语言、显式备用文字。`{name}` 参数只替换一次，不递归解释参数中的内容。此版提供基础参数与回退接口，复数/ICU 与完整应用语言切换可继续扩展，不在源包内假装已实现。

主程序使用 `uiText` 和 `Context.uiCopy` 读取宿主目录。新增宿主词条需在简体中文、英语和韩语目录中保留相同键名与参数；轻松版可以只覆盖不同措辞。插件不得把文案键当文件路径或网页脚本；字典导入限制范围、数量和长度。

## 启动与空白提示

启动的实际字幕正文为空。`AppState.subtitlePlaceholder` 只在正文空白时显示，未自定义时跟随界面语言；“设置 → 外观 → 字幕 → 空白提示”可更改、多行或留空，最多 200 个 Unicode 字符，不切断 emoji。

此偏好保存为 `appearance.subtitlePlaceholder`，重启后保留。占位不写入 `editorValue`，不进入朗读、导出、插件正文或自动适应字号的计算。输入一个字后占位消失；清空真实正文后重新出现。取消设置弹窗不会保存。

`SubtitlePlaceholderStore(context, defaultPlaceholder)` 可注入本地化默认值；用户未自定义时使用该默认值，已有自定义不被语言变化覆盖。Agent 可通过 `AppCommand.Setting("subtitle.placeholder", value)` 修改同一偏好。
