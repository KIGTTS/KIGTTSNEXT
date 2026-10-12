# 插件 API v8

API8 新增 [合成生命周期](tts-lifecycle.md)：通用预热、释放、常驻策略及实际播放活动信号，提供者实例 generation。模型与平台连接缓存、两分钟空闲释放和短段 PCM 完全由插件实现；保持加载统一在语音合成服务设置。当前引擎包为 0.7.0/API8，合成服务为 0.7.3/API8、识别服务为 0.7.1/API8，旧 API7 及更早的包需同发布者重建，升级保留数据。

API7 引入的 [快捷文本/Agent 接口](quick-text-plugin-api.md) 继续使用协议 1：读写分组和卡片、批量预览提交、HTML 工具/媒体及文件管理、导入导出、变更通知和原生编辑入口。

API6 新增大字幕 `PresentContent`、`SubtitleContent` 媒体协议和当前内容上下文。图片/动态照片、视频、插件 HTML、动作消息与资源归属详见 [媒体接口](media-content.md)。苹果实况照片默认关闭，由宿主实验功能设置统一控制。API5 及更早的 DEX 二进制须同签名重建，现有资源保留。

插件实现 KigPlugin，声明 PluginDescriptor、可选 TtsEngine / AsrEngine 与 PluginContributions，然后由宿主 PluginRegistry 注册。内置功能 UI 仍为源码模块；外部引擎通过 .kigplugin 安装，在独立工作进程中加载。宿主注册远程代理，具体引擎类与依赖不进入主程序。包、生命周期、目录与限制详见 [专用插件包](plugin-packages.md)。

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

动作 ID 在同一插件内唯一，宿主按 pluginId/actionId 命名空间路由，以 order 和命名空间稳定排序。API 主版本必须匹配（当前 5）。API2/API3/API4/API5 的二进制包必须重新构建，同发布者递增版本更新可保留模型和配置。attach 提供插件专属存储与资源环境；close 在取消并结束会话后调用。

## 手势贡献

PluginContributions.gestures 可返回 GestureAction，当前规范入口为 PluginGesture.QUICK_TEXT_LONG_PRESS。宿主调用同一插件的 execute(actionId, context)，手势动作 ID 与按钮动作 ID 共用唯一命名空间。

没有贡献时长按无行为，也不提供长按无障碍动作；普通点击和上滑仍用于展开快捷文本。手势冲突暂按注册顺序选择第一个贡献者；语音引擎由插件管理页面选择并持久化，不与手势入口绑定。语音输入插件可动态移除手势并返回 RefreshUi，开关由插件持久化。

## 动态贡献、宿主行为与页面

contributions(context: PluginContext) 可根据当前编辑文本、聆听字幕与状态动态返回入口，默认实现读取 contributions 属性。listening 表示识别任务是否运行，listeningPanelVisible 表示聆听面板是否开启，两者独立：任务结束后面板可继续展示并提供关闭入口；因空间不足临时隐藏不改变面板开启状态。新增字段默认取 listening，兼容原来的调用方式。

execute(actionId, context) 可返回：

- Message、Dialog、Menu：显示消息、弹窗或动作菜单，菜单中的 PageAction 继续交给同一插件。
- OpenPage、ReplaceEditorText：打开该插件页面或替换编辑文本。
- RefreshUi：插件内部配置已变化，通知宿主重新读取贡献与页面。
- HostAction：请求规范 HostCommand。目前包括 TOGGLE_LISTENING、CLOSE_LISTENING_PANEL、USE_STREAMING、USE_BATCH。
- SelectTtsService：请求将已启用的合成服务插件选为应用的统一 TTS 入口。

DrawerEntry 声明抽屉条目和目标页面（竖屏右侧、横屏左侧）。page(pageId, context) 返回 PluginPage，由 PageBlock.Text、Notice、Actions、Group 构建。宿主按当前亮色/深色主题渲染页面，保留父页状态，统一提供完整滑入/退场、父页同步过渡、层级返回与预测性返回；预测提交从当前姿态继续，取消复原，退出页完全离屏后才移除；返回优先关闭抽屉。插件管理集中于“设置 → 插件管理”，管理页打开的插件页面返回管理页；抽屉直接打开的插件页面返回原页面。插件不持有 Activity、View 或 Compose 实例。

二/三级页面使用原生 `LargeFlexibleTopAppBar`（画板使用固定小顶部栏），滚动收折并保留父页姿态；背景和页面转场延伸至状态栏，操作仍避开系统栏/刘海。子页顶部没有常驻抽屉入口。托管插件页面的顶层 `PageBlock.Actions` 会映射到该页顶部操作，前两项直接显示，剩余项进入更多菜单；`Group` 内动作仍在组内。操作按当前页面独立注册，实时更新使用当前回调，增删有淡入淡出和宽度过渡，退场操作禁用。插件任务的环形进度在展开顶栏位于返回键右侧，顶栏收为单行后位于标题右侧，不改变列表位置；声明块、列表新旧条目、提示与选中标记有出入动效。页面渲染沿用原协议；本轮 SDK7 更新要求重建外部包。

声明块及嵌套 Group 的增删自动由宿主 `MotionColumn` 展开淡入/收拢淡出；Text、Notice 内容更新局部淡变，更新其它文本不会复制或重建正在按住的动作。动作以 ID 保持身份，Group 以标题与同名出现序号识别，未提供 ID 的文本/提示以类型和位置识别。退场项保留原相邻位置并立即禁用操作，快速恢复同一项沿当前动画继续。管理列表继续使用稳定条目 ID 的出入/位置过渡；插件无需增加动画依赖或升级包格式。临时 busy 或页面转场锁定保留动作与开关配色，同时禁用输入及语义，避免返回时闪灰；按钮使用原生 animated shapes 并在按下即触发，短按保留最小反馈脉冲，取消及离页正确释放。自定义网页的 DOM 动画仍由页面作者控制。

宿主为贡献按钮、菜单条目、Tab、抽屉入口及声明式页面操作统一提供系统触感反馈，动作提交时触发一次。插件不需要另行震动，避免重复反馈。全局开关位于“设置 → 触感反馈”，同样控制长按触感；滚动、取消与禁用操作无点击反馈。

设置类入口将 DrawerEntry.settingsPage 设为 true：集中出现在“设置 → 插件管理”内，不占用抽屉功能列表。默认 false 保持功能页入口行为；语音输入设置与界面调试已标记为设置页。

## 内置示例

- placeholder-tts / placeholder-asr：只作为测试夹具，生产 APK 不包含占位引擎。
- listening-panel（字幕插件）：按 listeningPanelVisible 动态注册“开始聆听 / 关闭聆听”、识别模式和上面板收起按钮，发出宿主命令，自身不实现 ASR 引擎。TOGGLE_LISTENING 在面板开启时关闭面板并取消任务，关闭时启动识别并显示面板。面板没有标题栏，缩放入口由宿主放右下，滑条临时替换左侧按钮区。
- voice-input：默认关闭，通过 QUICK_TEXT_LONG_PRESS 注册可选的快捷文本长按入口，关闭后不贡献手势。开启后请求宿主录音最多 5 秒，通过所选 ASR 完整录音接口更新大字幕；不打开聆听面板。
- debug-ui：13 个扩展区域各配置 0–20 个入口、图标/图标文字样式并持久化，默认数量为零；演示菜单、弹窗与自建页面，配置集中于插件管理。

## 引擎能力与调用

TtsInfo 声明资源就绪状态、可选声音列表和单次文本长度；TtsRequest 支持语言、语速和声音 ID。SynthesisEvent.Audio 必须返回 PCM16，宿主负责播放。主程序没有系统 TTS、Piper、sherpa 或 ONNX 的引擎实现。

## 合成服务与语音包管理

`TtsInfo.role` 区分 `PROVIDER`（默认，独立推理引擎）和 `SERVICE`（统一路由服务）。服务启用时应用选择服务；提供者与声音由服务插件自行选用。ASR 选择不受影响。停用服务后可重新选用提供者。

声明 `speech-service` 能力的独立插件在 `PluginEnvironment.speechBroker` 得到通用代理：

- `providers()` 返回已安装的提供者 ID、名称、`enabled` 与 `TtsInfo`；停用提供者使用规范目录缓存，只可查看与排序，实际合成/修改需先启用。不暴露其它插件实现或服务，禁止循环嵌套。
- `synthesize(pluginId, request)` 返回可取消、有背压的 PCM 流；实际推理始终在目标提供者进程。
- `command(pluginId, VoiceCommand)` 转发规范管理操作。服务与引擎的资源格式、选用规则、UI 和保底策略由插件实现。

提供者可实现 `KigPlugin.voiceManager` 并声明 `voice-packs`，同时设置 `TtsInfo.managesVoices = true`。`VoiceCommand` 支持 IMPORT、SELECT、DELETE、RENAME、REFRESH、UPDATE_METADATA、SET_AVATAR、EXPORT、REORDER；`selectedVoiceId` 表示当前默认声音，`importsVoicePacks` 表示可导入，`VoiceInfo.deletable` 控制删除/重命名入口。没有 VoiceManager 的提供者仍可列出声音，服务直接将选定 ID 放入 TtsRequest；REFRESH 仅刷新通用代理快照。

IMPORT 的 `resourcePath` 是服务自身数据目录下的相对路径。broker 将文件复制到目标引擎的 `tmp/broker-*.voicepack` 后传入目标相对路径，管理调用结束始终清理副本；目标引擎负责验证、安装到自己的模型目录。不能传任意绝对路径。导入原件由服务清理，模型和配置始终归对应插件。删除确认由服务声明式菜单发起，删除同包全部声音时应明确提示。

现有语音合成服务内置系统 TTS，默认即可用；系统服务与语言数据由 Android 提供。外部提供者未配置、停用或在当前分段输出音频前失败时使用系统 TTS；已输出该段音频后出错则结束并报告失败，避免重复朗读。长文本按提供者上限分段并保持 Unicode 代理对完整。取消传播到当前引擎，不触发保底重播。

AsrInfo 声明输入格式、完整录音能力、录音长度上限、资源就绪状态及 StreamingKind：NONE、SEGMENTED（离线分句）或 INCREMENTAL（增量解码）。recognize 接收完整 PCM；recognizeStream 接收有背压的 PCM 流，Partial 是可选的真实中间结果，Final 可以表示一句话结束，EOF 冲刷最后一段。不得伪造中间识别文本。引擎在空输入、取消、失败时释放自己的资源，不吞掉 CancellationException。

音频格式为有帧对齐的有符号小端 PCM16，8–192 kHz、单/双声道；宿主按 AsrInfo.inputFormat 录音，格式不支持时报告 EngineException。跨进程 PCM 分块最大 128 KiB，使用管道避免 Binder 大事务。失败分为未配置、格式不支持、初始化、推理、超时、不可用。

PluginOutcome.ImportResource 请求宿主选择并复制文件或 ZIP 到该插件数据目录，完成后按 actionId 回调，inputs["resourcePath"] 为相对路径。TextInput 请求原生输入弹窗，提交后以 inputs["value"] 回调。网络下载由环境接口提供并写入该插件目录。设置仍由插件自己管理，宿主不识别引擎专属参数。

## 当前边界

外部包首版支持 Android DEX 与可选 JNI；Lua/Python 尚未集成。每插件独立普通工作进程，不构成权限沙箱，现阶段仅用于可信插件。声明式 UI 和运行上下文通过节流快照实时同步，按钮仍由宿主渲染。当前最多同时启用 8 个外部插件；复杂自定义控件、第三方手势争用选择和真正的权限隔离需后续协议升级。

## 通用条目管理页（API4）

PluginPage.management 可返回 ManagementList，宿主使用原生列表组件渲染。ManagementItem 提供稳定 ID、标题、说明、图标/头像、选中/置顶状态、分区、主操作、菜单和可选启停开关。ManagementFilter 提供分类筛选；插件只提供数据，不传入 Compose、控件尺寸或自定义样式。设置类页面的分组与条目规范见 [插件页面风格](plugin-page-style.md)。

reorderAction 接收 inputs["order"] 中完整的 JSON ID 列表，宿主支持长按或小手柄纵向拖动、海拔浮层、落位动画、边缘滚动和菜单上下移动。拖动过程中仅更新本地顺序，落位后提交一次；相异 group 不允许跨区移动。插件应使用 ItemOrder.validate 校验当前条目集合，持久化顺序后返回 RefreshUi。ItemOrder.reconcile/move 可供其它条目页复用。图片以 PluginResource 引用自身或服务所管理的提供者数据文件，路径始终为对应 Appdata 下的相对路径，读取不创建已卸载插件的数据目录。

```kotlin
PluginPage("records", "我的条目", emptyList(), management = ManagementList(
    id = "records", items = records.map { ManagementItem(it.id, it.name, primaryAction = "open/${it.id}") },
    reorderAction = "save-order"
))
// execute("save-order", context):
val order = PluginJson.decodeFromString<List<String>>(context.inputs.getValue("order"))
ItemOrder.validate(order, records.map { it.id })
// 将 order 保存到本插件 configuration，再返回 PluginOutcome.RefreshUi。
```

TtsInfo.packs 区分语音包与 VoiceInfo 声音，VoiceInfo.packId 建立归属；多说话人模型在统一页只有一条包记录，进入详情选声音。VoicePackInfo 提供名称/备注/置顶/顺序、头像引用与可编辑/导出/删除能力。新增 UPDATE_METADATA、SET_AVATAR、EXPORT、REORDER；IMPORT 传入 packId 时更新已有包。头像导入副本仍由 broker 放到目标插件 tmp。停用提供者的规范目录从缓存读取，enabled=false；服务不得合成或修改停用引擎，启用后刷新目录。缓存是协议数据，不是引擎实现或模型解析。

PluginOutcome.ExportResource 请求原生“另存为”，支持 MIME 类型、文件名、资源引用和临时标记；临时文件在完成/取消后清理，旋转时保留请求。TextInput.multiline 支持备注等多行输入。四角溢出菜单不抢输入焦点，插件模态菜单/输入期间暂停主字幕 IME 退出判定，关闭后恢复编辑键盘。次级页面返回与预测性返回固定采用入场侧，忽略手势边缘的方向。

## 识别服务与依赖（API6）

AsrInfo.role 为 PROVIDER / SERVICE，主应用识别选择仅接受 SERVICE。语音识别服务通过声明 asr-service 得到环境 recognitionBroker：目录 providers() 返回 AsrProvider（含启用状态和规范设置页面引用），recognize / recognizeStream 按目标 ID 调用独立提供者。服务与引擎禁止嵌套、自调用；完整录音和流式都采用有背压管道，取消逐级传递，输入/输出都必须明确结束。推理与模型管理始终在引擎插件。

PluginDescriptor.requires 可声明 RECOGNITION_SERVICE。依赖缺失时 Registry 不注册功能按钮/抽屉/手势入口，宿主也拦截过期 HostAction；低频配置仍可打开。当前字幕/语音输入使用此声明。服务存在但引擎未就绪时不打开麦克风，不直接回退提供者。

新增 SelectRecognitionService、RecognitionRoutingChanged、OpenPluginPage 与 SetPluginEnabled。跨插件页面使用正常导航；识别服务请求引擎启停时，宿主验证目标为独立 ASR、先取消音频，再变更进程状态并刷新目录。服务页选用引擎和打开模型页的具体用法见 [识别服务说明](recognition-service.md)。API5 及更早二进制需同发布者升级 0.5.0/API6，现有数据保留。
