# 界面语言与占位文字

API 9 预留界面本地化合同，当前主程序与内置示例文案仍以中文为主；这里不代表已完成所有语言翻译。

`PluginContext.locale` 与 `PluginEnvironment.locale` 传递 BCP 47 界面语言，例如 `zh-CN`、`en-US`。它们不修改识别包或朗读声音所支持的语言；`RecognitionConfig.language` 仍是另一项音频能力。

插件可覆盖 `KigPlugin.localization`，声明 `PluginLocalization(defaultLocale, supportedLocales)`。此信息进入跨进程 `PluginSnapshot`。页面名称、按钮、设置入口与状态文案由插件依据收到的 `context.locale` 生成；插件缓存语言相关页面时，应将 locale 加进缓存身份，或在语言改变时发送 `uiChanges`。

不可变资源可放在 `assets/i18n/<locale>.json`，通过 `PluginStrings.fromAssets(environment.assets)` 读取；格式为文案键 → 字符串。`text(key, locale, arguments, fallback)` 依次查询完整语言标签、上级语言、默认语言、显式备用文字。`{name}` 参数只替换一次，不递归解释参数中的内容。此版提供基础参数与回退接口，复数/ICU 与完整应用语言切换可继续扩展，不在源包内假装已实现。

主程序后续界面翻译使用 Android 资源，设置项、通用文件/聊天/代码控件共用宿主语言和主题。插件不得把文案键当文件路径或网页脚本；字典导入限制范围、数量和长度。

## 启动与空白提示

启动的实际字幕正文为空。`AppState.subtitlePlaceholder` 只在正文空白时显示，默认“点击输入文字”；“设置 → 外观 → 字幕 → 空白提示”可更改、多行或留空，最多 200 个 Unicode 字符，不切断 emoji。

此偏好保存为 `appearance.subtitlePlaceholder`，重启后保留。占位不写入 `editorValue`，不进入朗读、导出、插件正文或自动适应字号的计算。输入一个字后占位消失；清空真实正文后重新出现。取消设置弹窗不会保存。

`SubtitlePlaceholderStore(context, defaultPlaceholder)` 可注入本地化默认值；用户未自定义时使用该默认值，已有自定义不被语言变化覆盖。Agent 可通过 `AppCommand.Setting("subtitle.placeholder", value)` 修改同一偏好。
