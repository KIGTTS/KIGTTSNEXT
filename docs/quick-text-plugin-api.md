# 快捷文本与 Agent 插件接口（API10，兼容 API9 / 协议 1）

插件清单声明 `quick-text-read` 或 `quick-text-write` 后，可从 `PluginEnvironment.quickTextBroker` 获取绑定到自己的接口；write 同时允许读取。宿主和 UI 共用一个快捷库及媒体仓库，后台可读写，打开原生页面需要宿主 UI 存在。该接口不引入 Agent、LLM 或语音引擎依赖。

```kotlin
val broker = requireNotNull(environment.quickTextBroker)
val features = broker.request(QuickTextCommand.Describe)
val snapshot = (broker.request(QuickTextCommand.Query(limit = 50)) as QuickTextReply.Snapshot).value
```

## 数据和查询

分组和条目均有持久 UUID。分组改名后 ID 保持不变；UI 仍显示名称和图标。`QuickEntry` 提供正文、可选标题、分组 ID、四类卡片、媒体 ID、颜色、富文本、自动播放和声音策略。富文本使用 UTF-16 选区，包含粗体、斜体、下划线、颜色、字号；字号读取兼容旧范围 75–400，界面工具栏仍只有小/正常/大。

`QuickGroup.icon` 保留原有 `LIST/INFO/PERSON/FAVORITE/HOME/WORK/SCHOOL/TRAVEL/FOOD/HEALTH/SHOPPING/MESSAGE` 字符串，也接受 `symbol:<Material Symbols名称>`，如 `symbol:two_wheeler`。使用 `AppCommand.UiAssets("symbols", query = "wheel")` 查询宿主实际离线图标，不猜名称；未知名称在 Prepare 阶段拒绝，不部分改库。分组和网页条目共用搜索选取器，常用网格包含老版快捷文本和音效板的全部图标选择；改名、重启和导入导出保留实际字符串。

`metadata` 是预留给工具、模板、来源和配置标识的小型字符串映射：最多 32 项，键长 128，单值 UTF-8 4 KiB、总值 16 KiB。人工编辑、保存、系统恢复和导出保持该字段；较大的脚本、媒体和状态文件放插件数据目录。

Query 可按分组查询，每页 0–50 个条目，返回总数、nextOffset、库 revision 和相关媒体描述。第二页传同一 revision，库变化时返回 CONFLICT，避免拼接不同时间的结果。`includeContent=false` 只取标识与其它元数据；极长正文可通过导出文件读取。快照也返回当前显示分组、正在编辑条目（无 UI 时为空）。媒体描述提供类型、所有者、资源版本、主页面、MIME、HTTPS URL、适应/填充、缩略图焦点、实况信息，不暴露手机绝对路径。

## 批量预览和提交

Prepare 支持 PutGroup、PutEntry、RemoveEntries、RemoveGroups、MoveEntries、OrderEntries、OrderGroups。客户端为新增对象生成 UUID，一批可相互引用；单次 RPC 最多 100 个操作。排序需要完整、不重复的分组或条目 ID 集合。非空分组删除需明确移入其它分组或同时删除条目，至少保留一个分组。

```kotlin
val group = snapshot.groups.first()
val entry = QuickEntry(groupId = group.id, text = "你好", title = "问候")
val command = QuickTextCommand.Prepare("unique-request-01", snapshot.revision,
    listOf(QuickTextEdit.PutEntry(entry)))
val prepared = broker.request(command) as QuickTextReply.Prepared
val result = broker.request(QuickTextCommand.Commit(prepared.value.token))
```

Prepare 只构建并验证候选状态，不修改快捷库。token 绑定调用插件，十分钟有效；每插件最多四个、宿主共十六个预览。Commit 再检查 revision、附件和正在编辑的草稿，全部通过后一次持久化。非法或过期批次不会部分修改条目。删除条目或分组时返回 CONFIRMATION_REQUIRED；调用 OpenUi(REVIEW, token) 打开原生修改预览，由用户点击确认后提交。返回或 Discard 取消预览；导入的暂存资源在后台清理。

事务 ID 不可用于不同请求。重复 Prepare 或 Import 返回原预览或已完成回执，不重复创建；Transaction 查询准备中、已提交或已取消结果。最近保留最多 64 个回执，提交回执与数据同一次保存；重启仍可查。遇到 CONFLICT 应重新读取并使用新的事务 ID，不能盲目覆盖。

## HTML 工具和媒体

插件在自己的 `environment.storage` 创建页面项目，再发布：

```kotlin
val root = "resources/widgets/calculator"
environment.storage.file("$root/index.html").apply {
    parentFile!!.mkdirs(); writeText(html, Charsets.UTF_8)
}
val page = broker.request(QuickTextCommand.PublishPage(
    root = PluginResource(root), mainPage = "index.html", name = "计算器",
    allowedActions = listOf("calculator/result"))) as QuickTextReply.Media
val card = QuickEntry(groupId = group.id, text = "计算器", kind = QuickCardKind.PAGE,
    mediaId = page.value.id, metadata = mapOf("my.plugin/widget" to "calculator"))
```

项目根与主页面分别指定，支持 `pages/index.html` 引用 `../assets/tool.js`。脚本和图片必须位于项目根内。生成内容、导入素材、缓存和导出包均在 `appdata/Kigtts/plugin/plugindata/<pluginId>/`；宿主只保留媒体引用。发布网络页面须另声明 network。网页动作显式列入 allowedActions，沿用 `Kigtts.postMessage`，由插件 execute 接收；导入的其它插件网页不继承动作桥。

PublishMedia 支持图片、视频、动态照片和 HTTPS 页面，PublishAudio 支持音频。条目保存媒体 ID 与自己的播放策略。Presentation 可按资源版本修改适应/填充、自动/完整/焦点缩略图；文件内容与展示元数据分开处理。

当前工具卡采用 PAGE 类型：列表显示原生标题/缩略图，点击后在大字幕中运行 HTML。列表条目内直接嵌入多个交互 WebView 尚未实现；Describe.features 明确返回 `inline-widgets=false`，插件应使用已支持的页面方式。后续显示策略可通过 metadata 和能力发现扩展。

## 页面文件与原生入口

- Files：分页读取相对目录，每页最多 100。
- GetFile：把指定文件复制到调用插件自己的相对目标，返回 PluginResource；不返回绝对路径。
- PutFile / SetMain：按当前资源 version 原子写入或更换主页面。
- EditFiles：最多 100 个文件操作，支持新建目录、写入、移动/重命名、删除、指定主页面；先复制到 staging，验证最终主页面后替换项目，失败恢复原目录。

允许修改宿主拥有的页面和本插件的页面，禁止改写其它插件目录。正在人工编辑的页面返回 BUSY。文件版本变化返回 CONFLICT。HTML 主文件上限 UTF-8 2 MiB，单批资源上限 2 GiB、2048 个文件/目录；禁止路径逃逸、覆盖根目录和删除最终主页面。

OpenUi 支持 MANAGE、NEW_ENTRY、EDIT_ENTRY、PAGE_FILES、PAGE_PREVIEW、REVIEW。宿主使用正常层级页面与预测返回、原生动效和按钮反馈；已有人工草稿时拒绝破坏性的导航。插件页面编辑会复制为宿主项目，保持插件资源完整。

## `.kigcards` 导入导出

包是 UTF-8 `cards.json` 加相对 `media/` 资源的 ZIP，schema=1。可选择整个库、若干分组或条目；默认包含图片、音频、视频、实况配对片段、HTML 完整目录和展示设置。`includeMedia=false` 导出文字版卡片。导出保留空分组；没有设备路径。

Import 在调用插件自己的数据目录展开，校验路径、重复项、体积、分组/媒体引用和网络能力，重新分配 ID、避免分组重名，再返回 Prepare 预览。原 ID → 新 ID 对应表作为 mapping.json 资源返回，并保存到最终回执。提交前取消、失败或版本冲突不会改写原快捷库。包最多 2049 个 ZIP 项、展开后 2 GiB，cards.json 最大 8 MiB；拒绝 ZipSlip 和缺失资源。

```kotlin
val exported = broker.request(QuickTextCommand.Export()) as QuickTextReply.Resource
// 用原有 PluginOutcome.ExportResource 让用户选择保存位置。
val imported = broker.request(QuickTextCommand.Import(
    source = PluginResource("imports/cards.kigcards"), transactionId = "import-01",
    revision = snapshot.revision))
```

## 变更、传输和错误

`changes(afterRevision)` 返回可取消的 Flow，订阅时发送当前状态，后续通知库、页面及批准/取消变化。通知是可合并的失效信号，不能当成逐条事件日志；重连后重新 Query 或 Transaction。离开插件后取消订阅，连接关闭或进程死亡撤销通道。

RPC 请求 UTF-8 ≤256 KiB、回复 ≤512 KiB。大文件通过资源引用传递；TOO_LARGE 时减小批次或分页、采用 includeContent=false / 导出文件。错误包含 PERMISSION_DENIED、NOT_FOUND、INVALID、CONFLICT、BUSY、CONFIRMATION_REQUIRED、HOST_UNAVAILABLE、TOO_LARGE。未来操作通过 Describe 协商，新增字段可忽略。

JSON 使用稳定短标签，原生 Agent 与独立 Lua 运行时均通过相同 DTO 请求；Python 适配器可后续接入：

```json
{"type":"query","limit":10,"includeContent":false}
```

```json
{"type":"prepare","transactionId":"request-01","revision":12,"edits":[{"type":"entry.put","entry":{"id":"b09ca554-64fb-4d37-92ac-724514fda144","groupId":"dd54d0e5-7710-4b92-a844-206e042cbccb","text":"计算器","kind":"PAGE","mediaId":"page-id"}}]}
```

原生专用包使用 Android DEX；Lua 由可选 runtime.lua 外置插件审核与执行，主程序不携带 Lua/Python 解释器。Python 运行时尚未实现。原生插件使用本应用 UID，能力控制约束正式宿主接口，不构成权限沙箱。

## 示例与验证

可选独立包 `tools.quick-text-example-0.8.0.kigplugin` 创建计算器、提交结果字幕、导入导出条目，主 APK 不包含示例实现：

```powershell
pwsh -File tools/Build-EnginePlugins.ps1 -Modules quick-text-tools
node tools/Test-Calculator.mjs
```

本地回归覆盖正式 DTO、Binder、通知/取消、限额、归属、版本、幂等、整批回滚、人工编辑保留扩展字段、原生删除预览和示例计算器。实际独立进程、系统选择器和硬件交互的当次结果见 [Release 验证记录](release-testing.md)，不能将本地回归等同真机验收。

## 宿主操作与外置 Agent

`PluginEnvironment.appBroker` 需声明 app-control，提供能力发现、字幕、朗读/自动朗读、原生导航、已开放设置及明确导出的跨插件动作。先请求 Describe 获取真实目标和操作，不把页面中任何按钮都当成自动化接口。只有目标 `automationActions` 允许 PluginAction；API10 新增同步 PluginCall，返回实际动作结果。DeliverResource 普通交付仍送到目标 inbox 打开检查；Lua 的 agent-install 交付必须先获得原生批准或完全访问授权。

`tools.cloud-agent-0.9.0.kigplugin` 为独立云端助手，支持 DeepSeek、GLM、Kimi 和自定义兼容服务、多会话管理、会话模型切换、聊天气泡、流式回复、取消与工具调用。服务商公开的 reasoning 可选展示，默认关闭时显示简短摘录摘要；不是请求模型隐藏内部过程。密钥、会话和工作文件均在助手自身目录，主 APK 不包含网络请求/Agent 实现。

Agent 可查询、准备和提交快捷库变更，使用 create_page 一次创建真实 HTML 页面卡片、批量管理及导入导出。请求批准模式在修改前暂停，批准卡片出现在输入框上方；完全访问模式按原生授权执行，仍校验版本冲突、资源归属和调用结果。Agent 可编写 Lua 多文件项目，经授权投递安装、启用并同步调试，实际结果返回模型。实际权限/工具流程见 [云端助手](cloud-agent.md)、[Lua 编辑器](lua-plugins.md) 与 [插件 API](plugin-api.md)。

`uiChanges` 与快捷库 changes 都是可合并的失效信号，重连后重新查询，不作为完整事件日志。插件界面采用 SettingsEntry 分类入口及宿主 Group/Input/Choice/Toggle/Chat/Files/SourceEditor，按钮和过渡继承设置样式；NONE 可省略图标。

可直接导入的 HTML 计算器、Lua 多文件与原生模板，以及包源 Schema/签名脚本，位于独立私有仓库 [KIGTTS-PLUGIN-TEMPLATES](https://github.com/LHT02/KIGTTS-PLUGIN-TEMPLATES)。模板的本地协议/编译验证与手机验收范围分开记录。

## 快捷文本选择器

插件需要用户选择已有快捷文本时，返回 `PluginOutcome.SelectQuickText`，宿主会显示只读选择器。选择器沿用主界面的卡片、圆角、主题色和反馈，分组 Tab 位于卡片列表上方，不提供编辑、删除或排序入口；支持 `QuickTextSelectionMode.SINGLE` 和 `MULTIPLE`，完成后将 JSON 数组写入指定 `actionId` 的 `inputs["quickTextIds"]`。插件应在该 action 中重新校验 ID，已删除的条目不能恢复旧内容。`QuickTextReply.Features.features["quick-text-selector"]` 会声明宿主支持的模式。
