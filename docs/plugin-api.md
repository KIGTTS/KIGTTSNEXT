# 插件 API v1（原型）

插件实现 KigPlugin，声明 PluginDescriptor、可选 TtsEngine / AsrEngine 与 PluginContributions，然后由宿主 PluginRegistry 注册。当前为进程内、编译期集成，尚无外部插件安装、签名或隔离。

## 语音接口

PcmAudio 使用 16-bit little-endian PCM，携带采样率和声道数；数据块必须包含完整采样帧。

- TTS：synthesize(TtsRequest): Flow<SynthesisEvent>，开始、音频块、结束。
- 非流式 ASR：suspend recognize(PcmAudio, RecognitionConfig): RecognitionResult。
- 流式 ASR：recognizeStream(Flow<PcmAudio>, RecognitionConfig): Flow<RecognitionEvent>，开始、中间结果、最终结果。

Flow 是冷流，遵守背压。输入流完成表示流式录音结束；取消收集必须释放会话资源，不再发送最终结果。异常向宿主传播，禁止吞掉 CancellationException；空输入不得编造识别文本。占位引擎不播放音频、不访问麦克风。

## UI 扩展位置

| UiSlot | 位置 |
| --- | --- |
| HEADER_START / HEADER_END | 横版 Logo 两侧 |
| EDITOR_TOP_START / EDITOR_TOP_END | 大字幕卡片左上 / 右上 |
| EDITOR_BOTTOM_START / EDITOR_BOTTOM_END | 大字幕卡片左下 / 右下，与清空 / 朗读同组 |
| EDITOR_MENU | 大字幕文本菜单条目 |
| LISTENING_BOTTOM_START / LISTENING_BOTTOM_END | 可调整高度的聆听上面板底部两侧 |
| QUICK_PANEL_START / QUICK_PANEL_END | 快捷文本面板拖动横条两侧 |
| TAB_START / TAB_END | 可滚动标签栏首尾 |

大字幕底部中间由宿主保留：输入时是移动光标的触摸拖动条，不提供插件注册位。参考图中的橙黄色标注框不渲染。

横条按下即提供形变和触感，真实拖动保持原生触摸阈值；插件入口不接管宿主的面板/光标手势。光标按 12 dp 步距移动字素，移动触感仅来自实际选区变化，边界无重复反馈。大字幕偏好以单字宽度相对于当前窗口的百分比持久化，宿主负责原生字号换算与自动适应，不向插件开放独立字号样式。聆听字号调整保持固定底部控件区。

UiPresentation.BUTTON 用于按钮；MENU_ITEM 仅用于 EDITOR_MENU。UiButtonStyle.ICON 注册图标按钮，ICON_TEXT 注册图标文字按钮。宿主统一提供本区域的配色、尺寸、圆角、弹性及按压行为，空间不足时将图标文字压缩为单图标；过多入口收进下箭头更多菜单。右侧从右向左添加，宿主核心按钮保持在外缘。Tab 首尾插件入口固定在分类滚动区两侧。菜单入口和标签入口沿用各自原生组件，插件不得提供独立视觉样式。亮色主页在默认对比度下统一为小按钮提供原生中档颜色角色，插件入口自动继承该配色与主题渐变。

Tab 使用与分组相同的原生 FilterChip、圆形外端与弹性接邻圆角，按压会联动邻居；固定插件入口保持独立于分组滚动。快捷文本全屏编辑仍保留两个扩展槽，第三方按钮继承相同样式，不参与用户分组排序。条目与分组的编辑、排序和删除确认由宿主管理。

扩展按钮的添加、撤销、名称/图标/样式切换由宿主统一提供出入及替换动效，退出快照不产生重复无障碍入口或菜单；点击按当前贡献 ID 路由，已撤销动作不执行。底部五个槽位保持稳定身份与宽度过渡，贡献变化不会重建正在拖动的快捷入口。聆听上面板压缩为单行时，宿主临时不显示其底部扩展按钮，空间恢复后还原。

动作 ID 在同一插件内唯一，宿主按 pluginId/actionId 命名空间路由，以 order 和命名空间稳定排序。API 主版本必须匹配。

## 手势贡献

PluginContributions.gestures 可返回 GestureAction，当前规范入口为 PluginGesture.QUICK_TEXT_LONG_PRESS。宿主调用同一插件的 execute(actionId, context)，手势动作 ID 与按钮动作 ID 共用唯一命名空间。

没有贡献时长按无行为，也不提供长按无障碍动作；普通点击和上滑仍用于展开快捷文本。当前按注册顺序选择第一个贡献者，第三方竞争/用户选择规则留待插件安装机制实现。语音输入插件可动态移除手势并返回 RefreshUi，开关由插件持久化。

## 动态贡献、宿主行为与页面

contributions(context: PluginContext) 可根据当前编辑文本、聆听字幕与状态动态返回入口，默认实现读取 contributions 属性。listening 表示识别任务是否运行，listeningPanelVisible 表示聆听面板是否开启，两者独立：任务结束后面板可继续展示并提供关闭入口；因空间不足临时隐藏不改变面板开启状态。新增字段默认取 listening，兼容原来的调用方式。

execute(actionId, context) 可返回：

- Message、Dialog、Menu：显示消息、弹窗或动作菜单，菜单中的 PageAction 继续交给同一插件。
- OpenPage、ReplaceEditorText：打开该插件页面或替换编辑文本。
- RefreshUi：插件内部配置已变化，通知宿主重新读取贡献与页面。
- HostAction：请求规范 HostCommand。目前包括 TOGGLE_LISTENING、CLOSE_LISTENING_PANEL、USE_STREAMING、USE_BATCH。

DrawerEntry 声明右侧抽屉条目和目标页面。page(pageId, context) 返回 PluginPage，由 PageBlock.Text、Notice、Actions、Group 构建。宿主按当前亮色/深色主题渲染页面，保留父页状态，统一提供完整滑入/退场、父页同步过渡、层级返回与预测性返回；预测提交从当前姿态继续，取消复原，退出页完全离屏后才移除；返回优先关闭抽屉。插件管理集中于“设置 → 插件管理”，管理页打开的插件页面返回管理页；抽屉直接打开的插件页面返回原页面。插件不持有 Activity、View 或 Compose 实例。

宿主为贡献按钮、菜单条目、Tab、抽屉入口及声明式页面操作统一提供系统触感反馈，动作提交时触发一次。插件不需要另行震动，避免重复反馈。全局开关位于“设置 → 触感反馈”，同样控制长按触感；滚动、取消与禁用操作无点击反馈。

设置类入口将 DrawerEntry.settingsPage 设为 true：集中出现在“设置 → 插件管理”内，不占用抽屉功能列表。默认 false 保持功能页入口行为；语音输入设置与界面调试已标记为设置页。

## 内置示例

- placeholder-tts / placeholder-asr：实现语音占位接口与说明页面，不添加设计图外的主页按钮。
- listening-panel（字幕插件）：按 listeningPanelVisible 动态注册“开始聆听 / 关闭聆听”、识别模式和上面板收起按钮，发出宿主命令，自身不实现 ASR 引擎。TOGGLE_LISTENING 在面板开启时关闭面板并取消任务，关闭时启动识别并显示面板。面板没有标题栏，缩放入口由宿主放右下，滑条临时替换左侧按钮区。
- voice-input：默认关闭，通过 QUICK_TEXT_LONG_PRESS 注册可选的快捷文本长按入口，关闭后不贡献手势。当前仅显示占位提示，不访问麦克风、不触发聆听。
- debug-ui：13 个扩展区域各配置 0–20 个入口、图标/图标文字样式并持久化，默认数量为零；演示菜单、弹窗与自建页面，配置集中于插件管理。

## 当前边界

尚无第三方插件包加载/卸载、权限申请、跨进程通信或沙箱。编译期注册不构成不受信任代码隔离。引擎暂使用首个声明相应能力的插件；引擎选择、兼容性协商与权限模型待后续确定。声明式页面当前仅提供上述块，复杂第三方控件需要扩展协议。
