# 插件开发合同

以主仓库 `plugin-api/src/main/kotlin/com/kigtts/plugin/api/` 为准。模板版本只标示源样板，不替代主 API 二进制版本；旧原生包需要同发布者重建，更新保留自己的 Appdata。

## UI 与动作

`KigPlugin` 提供 descriptor、contributions、page、execute、attach/close。`contributions(context)` 可依据当前文本/播放/识别状态变化；`uiChanges: Flow<Long>` 通知后台修改，宿主重新读取缓存。不要从 page 或 contributions 发起耗时推理/网络或写入操作。

`SettingsEntry(id,label,pageId,icon,cardId,cardTitle,summary,order)` 共享原生设置卡片。宿主管理 `appearance/audio/content/extensions` 的标题与顺序；其它合法 cardId 可增加插件卡片。`PluginIcon.NONE` 隐藏不需要的前置图标且不留位置。旧 DrawerEntry 会兼容迁入设置，不会恢复独立抽屉。

Lua 用 `contributions.settingsEntries` 的 camelCase JSON/表字段注册同样入口；运行时为入口与页面加模块前缀。原生 SDK 能力与 Lua schema 子集须分开看，详细字段见 [Lua 文档](lua.md)。

本地化接口包含 `PluginContext.locale`、`PluginEnvironment.locale`、`KigPlugin.localization`、`PluginLocalization` 元信息与 `PluginStrings` 资源回退。
标签仍是插件返回的字符串，宿主不会自动把 key 翻译成文案；插件应按当前语言构建页面并失效缓存。详见 [本地化](localization.md)。

主界面按钮通过 UiAction/UiSlot 注册，宿主统一尺寸、圆角、主题、短按形变、触感与溢出菜单。`ICON_TEXT` 空间不足时压为图标；菜单动作必须同时使用 `EDITOR_MENU` 与 `MENU_ITEM`。按钮与手势 ID 共用插件内唯一命名空间。

`automationActions` 仅声明可以由其它插件调用的公开操作。审核确认、启用未经检查代码、删除确认和私有后台动作不应在列表中。跨插件调用必须使用 `AppBroker`，不能通过 JSON 伪造调用者或存储归属。

## 原生页面

共享块包括 Text、Notice、Group、Actions、Toggle、Choice、Input，以及：

- `SourceEditor(id,text,saveAction,language="lua",readOnly=false)`：独立原生源码编辑体验，行号、键盘上方可滚动/展开快捷栏；保存输入为 `context.inputs["value"]`。最多 64,000 字符，更大源文件不要截断后写回。
- `Files(title,items,directory,openAction,operationAction,importAction?,createAction?,parentAction?)`：固定行高与多选、移动/复制目标目录选择。open 输入 `path`，目录浏览需要支持空路径根目录；parent 输入 `directory`。操作输入 `op/paths/destination`，paths 是 JSON 字符串数组，rename 的 destination 是新文件名。后端负责路径限制、目标冲突、回滚和错误报告。
- `Chat(messages,sendAction,stopAction,running,placeholder)`：原生聊天气泡、输入与流式状态。消息 ID 保持稳定；只展示提供者实际返回的 reasoning，默认不保存/展示详细推理。不要宣称能获取未公开内部思维链。

宿主提供 Large Flexible 顶栏、二/三级进入返回、固定预测返回方向与初始条目预载。只对真正新增、删除或更新内容触发局部动效；插件无需打包 Compose。网页 DOM 的排版与动效仍由网页作者实现。

## 宿主与快捷文本

`environment.appBroker?.request(AppCommand...)` 支持 Describe/State、导航、更新字幕、自动朗读、朗读/停止、公开设置、明确公开的插件动作和带审核的资源交付。先读取 Describe，不硬编码未公开目标、设置键或私有操作。

`environment.quickTextBroker` 支持查询、资源发布、分组/条目批量变更、排序、HTML 文件、导入导出和原生编辑。可靠写入按 `Query → Prepare(revision,transactionId,edits) → Commit(token)`；确认要求打开 REVIEW 页面交给用户。冲突重新查询；断线后按 transactionId 查询回执，不能盲目重复工具调用。`changes(afterRevision)` 是失效通知，需要重新查询且处理重新连接。

组和条目 ID 用 UUID。有权限读取不代表有权限写入，错误类型需要处理。网页通过 PublishPage 引用自己的资源目录和主文件，不直接读主程序存储，不给 iframe 或脚本传 Android 对象。计算器样例展示离线 HTML 和事务创建卡片；变更 UUID 后再做独立新工具。

`environment.stickerBroker` 读写共享自定义图库、贴纸和拍立得。`stickers-read/write` 与快捷文本能力分别授权。图片发布只接收调用方资源，图片或名片 PNG 读取会复制到调用方的 exports/tmp。`ImportMedia(mediaId)` 将快捷文本图片或实况静态照片复制为贴纸图片，`PublishQuickMedia(imageId)` 发布可用于 MEDIA 条目的主程序附件。批次使用 Query → Prepare → Commit，移除需用户原生审核。两套烟测模板使用 Query → Prepare → Discard 验证图库事务。完整字段、分页、存储和静态图片限制见 [主仓库图库与拍立得合同](https://github.com/KIGTTS/KIGTTSNEXT/blob/main/docs/stickers-api.md)。

## 资源与能力

原生 manifest 的 capabilities 是宿主连接授权：例如 ui、resources、app-control、quick-text-read、quick-text-write、stickers-read、stickers-write；引擎/服务按实际角色申请相应能力。Lua manifest permissions 是运行时子集，与原生 capabilities 不互换。

可变资源使用 `environment.storage.file/directory`，下载使用 environment.download；不可变打包文件位于 environment.assets。网络文件、临时资源、缓存、配置和模型都在自己的 `appdata/Kigtts/plugin/plugindata/<id>/`。原生 DEX 仍为可信同 UID 扩展，独立进程是崩溃隔离，不声称权限沙盒。

### 拍立得、品牌边框与 Unicode 二维码

`QueryFrames(offset,limit,revision)` 返回可用的边框样式及 `format`。MINI／SQUARE／WIDE 使用富士三款相纸布局；不要用固定 4:5 去拉伸品牌边框。自定义样式 ID 必须属于当前插件命名空间，先发布透明 PNG 图片，再用 `frame.put` 注册资源；发布者和版本由宿主决定。原始相机 JPEG 的 `originalImageId` 与显示图 `imageId` 分别保存，更新设计时保留原图引用。

`CreateQrCode(text)` 需要 `stickers-write`，按 UTF-8 编码，支持中文、emoji、组合字符，最多 2048 字节。操作返回 `StickerReply.Image` 后，取得当前 revision，再通过正常 `Prepare/Commit` 将图片加入贴纸库或拍立得 IMAGE 图层。IMAGE 图层的 `text` 可保留二维码原始内容作为可读元数据，不会渲染额外文字。不要裁掉二维码四周的白色静区。

以下调用应放在已授权的 `execute` 动作中，不能从页面刷新执行写入：

```kotlin
val broker = requireNotNull(environment.stickerBroker)
val created = broker.request(StickerCommand.CreateQrCode("你好，世界 🙂"))
if (created is StickerReply.Image) {
    // 使用 created.value.id，重新查询 revision 后准备正规事务。
}
val styles = broker.request(StickerCommand.QueryFrames())
// 检查失败回复和分页，再选择真实存在的样式 ID。
```

### 相册与浮窗预览

`QueryAlbums(offset,limit,revision)` 返回 `Albums(revision,items,total,nextOffset)`，每册具有 `id/name/cardIds`；`QueryAlbumCards(albumId,offset,limit,revision)` 返回正常 `Snapshot`，照片顺序遵循该相册。先查实际 ID，跨页固定 revision，冲突后重新查询。读取需要 `stickers-read`，写入需要 `stickers-write`；Lua 页面刷新也可做这两种纯查询。

相册编辑仍放在正常 `Prepare → Commit` 中：`album.put` 的 `id/name` 创建或重命名，`album.move-cards` 的 `albumId/cardIds` 移动照片，`album.order` 的 `ids` 排全部相册，`album.cards-order` 的 `albumId/cardIds` 排一册全部照片。排序必须包含集合内每个 ID 且不能重复。每张照片只属于一个相册；`user.polaroids` 是不能移除的默认相册。`album.remove` 的 `ids` 需要原生审核，照片会追加回默认相册，图片资源保留；它不等于删除照片。

`OpenUi(CARDS,cardId)` 打开拍立得图库中的浮窗预览，`OpenUi(EDIT_CARD,cardId)` 进入正常编辑子页；传入不存在的 ID 会失败。默认纸面是纯白 `0xFFFFFFFF`，不要自行把它改成奶黄色。旧 `StickerSnapshot` 八字段构造与 copy 仍可调用；从宿主查询得到的快照进行旧签名复制时，新相册字段会保留。相册是 API9 的追加能力，旧宿主须先通过 Describe 的 operations 检查再调用。
