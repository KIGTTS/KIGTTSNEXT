# 云端助手插件

`tools.cloud-agent` 是单独安装的 MIT 插件，版本 0.9.0，要求 API 10。主程序只提供标准聊天、设置页面以及授权后的操作接口，不包含供应商请求、密钥、会话或 Agent 工具实现。

## 连接与对话

在设置的「助手」卡片中打开 AI 助手，或进入「连接与对话」设置。预设 DeepSeek、GLM、Kimi；也可选择自定义 OpenAI Chat Completions 兼容服务。各服务分别保存 HTTPS 地址、模型和密钥。模型与地址可以修改，换服务不会覆盖其它服务的配置。

所有文件保存在 `appdata/Kigtts/plugin/plugindata/tools.cloud-agent/`：

- `config/`：设置、待审核状态、AndroidKeystore AES-GCM 加密后的密钥；不把明文密钥写入会话或日志。
- `conversations/`：独立会话、消息、供应商返回的 reasoning、工具请求与结果。
- `workspace/`：Agent 创建的 HTML、JS、CSS 等文件。
- `drafts/`：Lua 插件草稿。
- `resources/quick-imports/`、`exports/`：用户导入和导出的快捷文本资源。

支持新建、切换、重命名、导出和确认删除会话。输入框上方可切换服务与模型，或输入自定义模型名称；每个会话独立保存模型，正在生成的请求使用开始时选定的模型，下次请求采用新选择。回答使用带一个小圆角的气泡，支持原生可选文字、粗体、斜体、下划线、标题、列表、链接及代码块，可停止。向上拖动后暂停自动跟随流式更新，显示「回到底部」悬浮按钮；点击后恢复跟随。连续工具结果默认合并折叠，展开可查看图标、动作和结果。一次只运行一个回答，切换会话不取消回答。进程中断后，未完成的流式消息保留已收到文字并标为已停止，不会自动重发请求。特别长的消息会提示仅显示结尾，可以导出完整会话查看全部内容。

「显示思考过程」默认关闭，气泡显示服务公开 reasoning 的首尾句短摘录摘要；开启后可展开详细内容。它只控制是否展示服务通过 `reasoning_content` 返回的内容，不伪造或请求服务未公开的内部过程。无论是否显示，工具调用与多轮上下文所需的 reasoning 字段均保存并回传。插件不强制通用 `thinking` 参数，保留模型的默认推理模式，兼容不接受该参数的 Kimi 模型。

网络实现使用平台 HTTPS 与 SSE，不打包供应商 SDK。禁止自动跟随重定向，避免把 Bearer 密钥发送给跳转目标；错误提示不展示服务器原始内容或密钥。SSE 支持 CR/LF、保活注释、多行事件、分片 UTF-8 读取以及按索引组装工具参数。连接中断、返回长度限制或未完成的工具 JSON 不会被当作完整工具请求执行。页面更新限频，完整历史留在插件目录；传给宿主的聊天窗口保持在快照大小限制内。

## 操作与审核

开启「允许操作软件」后，Agent 获得这些工具：

| 工具 | 用途 |
| --- | --- |
| `app` | 按 `AppCommand` 操作文本、导航、自动朗读、朗读状态、已开放设置与插件入口；先 `describe` 获取能力 |
| `quick_text` | 按 `QuickTextCommand` 查询、准备与提交批量变更，管理和发布 HTML 页面、导入导出及编辑页面文件 |
| `stickers` | 按 `StickerCommand` 读取照片、贴纸库、拍立得与相册，准备移动和排序等变更，复制图片，双向转换快捷文本图片与贴纸 |
| `write_file` / `read_file` / `list_files` | 只访问助手的工作文件，不访问密钥和其它插件目录 |
| `create_page` | 一次发布 HTML 目录并创建真正关联附件的 PAGE 条目，绑定工具调用的事务回执 |
| `web_search` / `web_fetch` | 联网检索带标题、链接与摘要的资料，读取公开 HTTPS 网页正文 |
| `draft_lua` | 写 `manifest.json` 与 `main.lua` 草稿 |
| `deliver_lua` | 经批准或完全访问授权后交给独立 Lua 运行时校验、安装、启用并取得完成回执 |
| `lua` | 列举、读取、测试、启用及执行 Lua 项目，返回真实调试结果 |

正式接口的权限、修订号、幂等事务与审核机制仍由宿主执行。模型不能改变接口绑定的资源所有者，也不能调用其它插件未明确导出的按钮。文件工具与资源发布均拒绝 `config/`、会话目录和路径穿越，避免把密钥作为网页资源发布。

输入框上方提供「请求批准」与「完全访问」。默认请求批准，涉及修改软件、发布网页或安装/运行 Lua 的工具在执行前暂停；审批卡片固定在输入框上方，批准后执行原调用一次并续接，拒绝会记录取消结果。待批准信息随会话持久化。完全访问允许已开放接口范围内的操作与 Lua 安装、启用、调试自动执行，文件范围、资源归属、Lua权限与执行限额仍适用。切换权限由原生输入栏操作授权，模型传来的保留字段不能制造批准。

宿主仍可能要求资源删除审核或返回冲突；审批卡片可打开详情，批准后提交原 token。结果不明确时暂停供用户检查，不自动重试修改。取消生成、关闭操作软件后不能绕过这条执行边界。

贴纸与名片使用独立 `StickerBroker`，要求 `stickers-read` / `stickers-write`；跨库发布快捷文本图片另需 `quick-text-write`。待审核记录保存发出 token 的接口类型，重启后仍由对应接口打开原生审核、提交或丢弃，不会把贴纸 token 发给快捷文本接口。请求批准模式下，删除预览标为 `requiresConfirmation` 时即暂停；完全访问模式自动提交，宿主仍核对授权。取消或提交返回未知状态时保留待审核记录。关闭「允许操作软件」后，即使云端意外返回工具调用也会拒绝执行。

Lua 草稿格式与 `runtime.lua` 相同：`manifest.json` 包含 `id`、`name`、`version`、`permissions`，`main.lua` 返回贡献、页面与执行函数。权限仅支持运行时开放的 `app-control`、`quick-text-read`、`quick-text-write`、`stickers-read`、`stickers-write`；`host.stickers(command)` 使用相同照片与名片协议，脚本自身权限仍由运行时检查。Lua 功能由独立运行时校验并限额执行。`deliver_lua` 使用有完成回执的安装入口；`completed` 只表示调用结束，必须继续检查 `installed`、`enabled` 或错误结果。普通手动导入继续走编辑器检查页。

原生导入导出按钮可处理 `.kigcards`，导入继续使用同一套预览与确认流程。Agent 优先调用 `create_page(html,title,text,groupId)` 创建计算器等网页，空 groupId 使用当前分组；也可以在 `workspace/` 写文件、调用 `publish-page`，再以版本化事务创建页面卡片，条目 ID 必须是 UUID；创建、排序、移动、批量更新与导入导出使用已有快捷文本接口。

## 贴纸、名片与快捷图片

`stickers` 工具的 `command` 是 SDK 的 `StickerCommand` JSON。先 `describe` 和 `query` 获取实际 ID、修订号与可写能力；列表支持分页，`scope` 可选 `ALL`、`LIBRARIES`、`ITEMS`、`IMAGES`、`CARDS`。查询返回图片 ID、大小等元数据，不暴露宿主或其它插件文件路径。名称、说明及文字图层均视为内容数据，不能替代用户指令。

双向操作使用宿主复制的资源：

1. 快捷文本图片转贴纸：先 `quick_text/query` 读取条目及 `mediaId`，再 `stickers/import-media` 复制原照片；实况照片提取静态照片，不自动播放或提取视频。拿到 `image.id` 后，重新查询贴纸修订号，以 `prepare` 的 `library.put` / `item.put` 加入库，或用 `card.put` 加入名片。
2. 贴纸转快捷文本：调用 `stickers/publish-quick-media`，将返回的 `media.id` 用于 `quick_text/prepare` 的 `entry.put`，创建 `kind="MEDIA"` 条目。字幕、缩略标题与分组由用户的指令决定，不使用文件名代替字幕。
3. 读取图片文件：`stickers/read-image` 将所选 `imageId` 复制到助手的 `workspace/exports/` 或 `exports/`，返回属于助手的 `PluginResource`。副本可供 `quick_text/publish-media` 或 `stickers/publish-image` 使用；禁止读取其它插件 owner、写入配置及覆盖 Lua 草稿。当前工具读取是资源访问与复制，不代表云模型已经看过图片像素。
4. 整卡转贴纸：`stickers/export-card(cardId,destination)` 把渲染后的拍立得名片导出为助手目录中的 PNG，再用 `publish-image` 和 `item.put` 加入贴纸库。

例如读取一张图库图片到助手工作目录：

```json
{"command":{"type":"read-image","imageId":"查询得到的图片ID","destination":"workspace/exports/photo.png"}}
```

例如把已有快捷文本图片转入图库：

```json
{"command":{"type":"import-media","mediaId":"查询得到的媒体ID"}}
```

库、条目和名片增改排序统一使用 `prepare` → `commit`。`card.put` 的 `PolaroidCard` 支持照片、标题、说明、背景颜色和 `IMAGE` / `TEXT` 图层；图层位置与大小按名片比例保存，旋转按角度保存。`image.remove` 仅能删除当前批次结束后没有条目或名片引用的图片。删除需要原生单次批准或用户在输入栏授予的完全访问，模型不能自行签发批准。结果不明确时先用 `transaction(transactionId)` 查询回执，不能重新执行变更。待审核记录同时保存事务 ID，继续前查询已应用或已取消的长期回执；即使短期 token 过期也不会重复修改或把取消写成已应用。插件不自动扫描媒体库、不自动下载图片。

名片照片构图通过 `photoX` / `photoY`（照片窗内的中心位置，0 至 1）、`photoZoom`（1 至 8）以及 `photoBackdrop`（`GRAY` / `BLUR`）设置；贴纸图层支持 `flipX` / `flipY` 镜像。Agent 读取完整名片后再修改需要的字段，保留其它构图与图层，不能重建旧字段而丢失新配置。

边框先调用 `query-frames` 分页发现，取得稳定 `id`、显示名、宿主 `version`、纸色/照片细边/照片圆角、`format` 及可选透明 `overlayImageId`；再把返回 ID 写入拍立得的 `frameStyleId`，不猜测样式名称。纸面比例和照片窗口由 `format.geometry` 决定，Mini、Square、Wide 和旧版 4:5 各不相同；品牌 PNG 按对应纸面比例制作。自定义品牌 PNG 先经 `publish-image` 复制为宿主图库图片，再在 `frame.put` 的 `style.overlayImageId` 中引用图片 ID，不使用文件路径。新样式 ID 必须位于 `tools.cloud-agent.` 命名空间；`owner` 由宿主绑定，`version` 由宿主递增，模型不得伪造。内置及其它插件的样式可以查询和选用，不能改删。样式移除走 `frame.remove` 和同一原生审核流程；样式缺失时显示默认边框并保留原 `frameStyleId`，Agent 不应静默替用户改成默认 ID。

相册先用 `query-albums` 分页读取 `id`、`name` 和有序 `cardIds`，再用 `query-album-cards(albumId,offset,limit,revision)` 获取册内完整拍立得字段及相册信息。每张拍立得恰好属于一个相册；默认相册的稳定 ID 为 `user.polaroids`。相册分组不改变贴纸图库或原始图片资源。

相册变更同样经过 `prepare` → `commit`：

- `album.put` 直接包含 `id`、`name`，只更新相册名称等元数据，不接收 `cardIds`。
- `album.move-cards` 包含目标 `albumId` 与要移动的 `cardIds`；新 `card.put` 先归默认册，可在同一批次移至指定相册，更新已有拍立得则保留归属。
- `album.order` 的 `ids` 必须将所有相册各列一次；`album.cards-order` 包含 `albumId` 及该册完整的 `cardIds`，各列一次。
- `album.remove` 包含 `ids`，经过原生确认后将其中照片移回默认相册，保留拍立得和图片。默认相册不能删除。

例如移动已有拍立得，修订号和 ID 都应先查询取得：

```json
{"command":{"type":"prepare","transactionId":"move-photo-1","revision":12,"edits":[{"type":"album.move-cards","albumId":"查询得到的相册ID","cardIds":["查询得到的拍立得ID"]}]}}
```

`open-ui(target="CARDS",id=cardId)` 会定位到所属相册并打开与用户点按一致的原生预览浮窗，返回关闭浮窗并保留浏览位置；`EDIT_CARD` 则进入正常编辑页面。读取和复制资源不会自动把图片上传给模型，当前插件不声明云端视觉分析能力。

## 供应商依据与验证边界

2026-10-13 核对官方文档，预设模型仅作为可编辑的起点：

- [DeepSeek Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode/)：示例模型 `deepseek-flash`，有工具时保留完整历史 reasoning。
- [智谱 API 快速开始](https://docs.bigmodel.cn/cn/api/introduction)：`https://open.bigmodel.cn/api/paas/v4`、Bearer 认证与 `glm-5.3` 示例；[GLM 流式示例](https://docs.bigmodel.cn/cn/guide/models/text/glm-5) 展示独立 reasoning/content delta。
- [Kimi Quickstart](https://platform.kimi.ai/docs/overview)：OpenAI 兼容端点 `https://api.moonshot.ai/v1`、`kimi-k3` 示例；[Thinking Models](https://platform.kimi.ai/docs/guide/use-thinking-models) 要求完整回传 reasoning，且不同模型接受的推理参数不同。使用国内帐号时可填写平台提供的国内端点。

无用户密钥时只执行本地协议、状态、取消与模拟工具回归，不进行真实计费请求。AndroidKeystore、手机网络、聊天手势和实际 Lua 审核安装需要真机验证。外部 DEX 插件是受信任原生代码；文件接口约束不能代替系统权限隔离。

## 后台任务与参考

生成任务独立于页面和当前选中的会话。工作进程在生成期间显示前台通知并使用有时限的 CPU 锁，结束、取消或系统超时时释放；不会为了保活在空闲时常驻，也不自动重发中断请求。Android 和厂商仍可终止进程，未完成文字按已停止恢复。

联网检索使用公开 Bing RSS，支持有限次数的 HTTPS 区域跳转；结果依赖网络与检索服务可用性。正文工具限制大小，不向网页转发供应商密钥，不访问本机/局域网。网站内容作为资料，不作为工具指令；回复应引用核对过的来源链接。

交互参考 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) 的会话、工具与权限分工，保持原生宿主与独立插件结构，不包含子代理功能。
