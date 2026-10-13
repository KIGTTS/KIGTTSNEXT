# 图库、贴纸与拍立得接口（API9）

`PluginEnvironment.stickerBroker` 提供 typed `StickerCommand` / `StickerReply`，JSON 调用使用 SDK 的 `PluginJson`。主程序、Agent 和插件操作同一个 `StickerRepository`，不会维护各自不同的拍立得副本。图片的 ID 是资源标识，不是文件路径。

## 能力与资源归属

- `stickers-read`：读取图库、贴纸和名片，取得图片或渲染名片的副本。
- `stickers-write`：发布图片、修改图库/贴纸/名片、排序、移除与打开编辑页面；也包含读取。
- `ImportMedia` 另需 `quick-text-read` 或 `quick-text-write`。
- `PublishQuickMedia` 另需 `quick-text-write`。

`PublishImage(source)` 的 `PluginResource.pluginId` 必须为空或是调用方自身，路径只能在调用方 Appdata 内。主程序先复制再验证图片；修改原文件、移除快捷文本附件或卸载图片来源插件不会改变已导入的贴纸。

`ReadImage(imageId,destination)`、`ExportCard(cardId,destination)` 返回 `StickerReply.Resource`，其中 `source` 是**调用方自身目录中的实际副本**。副本目的地只允许 `exports/`、`tmp/`、`workspace/exports/`，以及外置编辑器的 `projects/<id>/data/exports/` 或 `projects/<id>/data/tmp/`。不能覆盖已有文件，也不能写入调用方的配置或其它插件的目录。回复不会包含主程序或其它插件的绝对路径。

## 数据与分页

先 `Describe` 查看可用操作，再 `Query` 获取当前 revision 和真实 ID。`StickerSnapshot` 包含：

- `libraries`：有稳定 ID 的自定义图库，列表次序就是图库排序。
- `items`：`id/libraryId/name/imageId`，同一图片可以用于多张贴纸或名片。
- `images`：`id/mime/bytes/width/height/version`，没有 URI 或路径。
- `cards`：`PolaroidCard(id,title,description,imageId,layers,frameColor,photoX,photoY,photoZoom,photoBackdrop,frameStyleId,originalImageId)`。`imageId` 是显示图，`originalImageId` 可保留内建相机未加滤镜的原始 JPEG；两者都纳入资源引用检查。

`Query.scope` 可选 `ALL/LIBRARIES/ITEMS/IMAGES/CARDS`，`libraryId` 筛选图库及其贴纸；`offset` 起点、`limit` 为 1–50。`ALL` 对各集合使用相同的分页区间，`total` 是这些集合中的最大长度；按 `nextOffset` 继续查询并合并各集合。分类型查询的 `total` 则是该集合长度。传入 `revision` 可避免把不同版本的分页结果混在一起。`changes(afterRevision)` 是失效通知，收到通知后重新读取。

图层 `CardLayer`：

- `kind=IMAGE` 使用 `imageId`；其 `text` 可保存最多 2048 UTF-8 字节的可读元数据（如二维码原始内容），**不会作为文字渲染**，空白和换行会原样保留。`kind=TEXT` 使用 `text`，支持文字和系统可显示的 emoji。
- `x/y` 是**整张拍立得纸面**的位置比例（0–1），不只照片区域。
- `width` 为整卡宽度比例（0.01–2）；图片按原图比例计算高度，文字用该值计算字号。
- `rotation` 是角度，`color` 为 ARGB 整数；图层列表次序是从底到顶的层次。
- `flipX/flipY` 镜像图层内容的局部轴，再应用旋转；文字与图片均可镜像。

拍立得照片使用 `photoX/photoY`（0–1，照片窗口中的图像中心）、`photoZoom`（1–8，相对 Fit 的倍数），`photoBackdrop=GRAY/BLUR` 指定照片留白填充。修改构图不会改变原图。旧记录默认居中、1 倍和灰色；PNG 与原生预览使用相同保存值。

主程序原生预览、相机裁切与 PNG 导出读取 SDK `PolaroidFormat.geometry` 的同一份相纸及照片窗口数据。`imageId=null` 的拍立得也可由文字和贴纸构成。

| 格式 | 相纸宽×高 | 照片窗口宽×高 | 照片裁切比例 | PNG 宽×高 |
| --- | --- | --- | --- | --- |
| MINI | 54×86 mm | 46×62 mm | 46:62 | 942×1500 |
| SQUARE | 72×86 mm | 62×62 mm | 1:1 | 1256×1500 |
| WIDE | 108×86 mm | 99×62 mm | 99:62 | 1884×1500 |
| LEGACY | 虚拟 4:5 旧布局 | 旧 1072×1128 坐标单位 | 1072:1128 | 1200×1500 |

三款布局使用本轮给定的相纸及照片尺寸。照片水平居中，顶部取 6 mm、底部留 18 mm，标题与说明放在底部安全区；这些白边及文字位置是软件排版选择，不声称所有边距都是厂家标准。`geometry.millimeterSizing=false` 标明 LEGACY 使用虚拟坐标。新建默认 Square；旧 catalog 缺失 `frameStyleId` 时按旧 classic 读取，旧名片迁移也保留 4:5。

拍立得主页的 FAB 提供拍照、选图和新建空白；单击预览，长按或预览编辑键进入编辑。“裁剪/贴纸”两个 Tab 分开管理照片与贴纸。照片双指移动/缩放，贴纸双指移动/缩放/旋转并提供旋转、缩放和镜像手柄。内建相机按 `geometry.photoAspectRatio` 选择预览/裁切比例，保留原始 JPEG 与处理图，选择原图时可使用同一资源 ID；相册保留原图比例。导入不会把照片压到 2048：源文件字节和原分辨率耐久保存，仅预览、导出按预算采样并处理 EXIF 方向。

## 预览、提交与审核

使用 `Prepare(transactionId,revision,edits)` 准备原子批次，再 `Commit(token)` 提交。每批 1–100 个操作：

| JSON type | 编辑类型 |
| --- | --- |
| `library.put` / `item.put` / `card.put` | 新增或更新图库、贴纸、完整名片设计 |
| `library.order` / `item.order` / `card.order` | 以稳定 ID 列表调整顺序 |
| `library.remove` | 移除图库，可将贴纸移往 `moveTo`，或指定 `deleteItems=true` |
| `item.remove` / `card.remove` | 移除贴纸或名片 |
| `image.remove` | 删除无引用图片资源；同批可先移除/修改其所有引用 |
| `frame.put` / `frame.remove` | 注册、更新或移除调用方发布的自定义装饰边框 |

排序必须恰好包含对应集合的每个 ID。至少保留一个图库。被任何贴纸、名片照片或图层引用的图片不能删除，批次会整体失败。

移除需要用户在原生审核页确认。`Prepared.value.requiresConfirmation` 或 `Failure(CONFIRMATION_REQUIRED,reviewToken)` 出现后，调用 `OpenUi(REVIEW,token)` 并等待用户操作，插件不能自行批准。原生确认会提交一次；随后重复 `Commit(token)` 返回同一 `Applied`，不会重复修改。已提交 token 的幂等记录绑定调用方并保留十分钟；事务回执通过 `Transaction(transactionId)` 查询，保留最近有限记录，能跨主程序重启读取。重复准备同事务、同内容返回原结果；把事务 ID 用于不同内容会返回冲突。

`Discard(token)` 丢弃未完成预览；已经应用的修改不能伪装为丢弃。失效、其它调用方的 token 和过期 revision 均拒绝。名片正在原生编辑时，插件不能改写它的草稿。失败回复不包含底层文件路径。

## 快捷文本图片双向使用

快捷文本图片变为贴纸：

1. 通过 `quickTextBroker.Query` 取得条目的真实 `mediaId`。
2. `StickerCommand.ImportMedia(mediaId)` 返回耐久复制的 `StickerImage`；图片或实况照片均支持，实况只复制静态照片。
3. `PutItem(StickerItem(libraryId,name,image.id))` 加入图库，或将 `image.id` 放入名片照片/图层。

贴纸放入快捷文本：

1. `StickerCommand.PublishQuickMedia(imageId)` 返回主程序持有的 `QuickMedia`。
2. 通过 `quickTextBroker.Prepare` 新建 `QuickEntry(kind=MEDIA,mediaId=reply.value.id,text=字幕)`，再提交。

整个名片也可以 `ExportCard` 取得 PNG 副本，再 `PublishImage` 转为图片资源，之后按上述步骤用于贴纸或快捷文本。复制出的快捷文本媒体不依赖原贴纸资源。

## 可扩展装饰边框

`PolaroidCard.frameStyleId` 新建默认 `instax.square.classic`。`QueryFrames(offset,limit,revision)` 返回 `Frames(revision,items,total,nextOffset)`；先读取真实样式及格式再指派。旧 `classic/night/slim/soft` 保留 LEGACY。三款新规格分别提供 `instax.mini.*`、`instax.square.*`、`instax.wide.*` 的 `classic/night/slim/soft` 原创边框；照片窗口由格式共享几何决定，以保证编辑、相机和导出一致。

`PolaroidFrameStyle` 具有 `id/name/version/owner/format`（LEGACY、MINI、SQUARE、WIDE）、可选 `color/borderColor`、`borderWidth`（整卡宽比，0–0.02）、`photoCornerRadius`（整卡宽比，0–0.1）和可选 `overlayImageId`。`color=null` 时使用拍立得自身 `frameColor`。装饰图片按该格式的完整纸面比例缩放，在照片上方、标题与贴纸下方；建议用带透明照片窗口的 PNG。资源仍先 `PublishImage`，不能通过边框声明引入外部路径。

`frame.put` 使用 `style` 字段，ID 必须在 `<调用方pluginId>.<局部样式名>` 命名空间中，发布者由宿主绑定，更新版本由宿主递增。最多 128 自定义样式，内置样式单独计数；插件不能改写内置或其它插件的边框。边框图片也纳入 `image.remove` 引用检查。`frame.remove(ids)` 经原生删除审核，可与移除其图片同批完成；拍立得保留原 `frameStyleId`，找不到样式时按既有合同回退旧经典。将来重新注册同 ID 可恢复选择。

catalog 仅保存自定义样式，读取时合并当前内置样式；内置色彩或名称更新不会让有效旧名片变成损坏数据。本接口可承载后续品牌合作资源，当前只含原创默认样式，没有内置第三方品牌素材。

API9 新增模型字段仍保留旧六、十、十一字段的 JVM 构造、默认构造、copy/copy-default，以及旧九字段边框构造/复制符号。旧 catalog 注入 classic 保持既有排版，新 JSON 创建使用 Square 默认值；`originalImageId` 的加入不会让旧 copy 丢掉已保存的原图引用。

`CreateQrCode(text)` 是正式写操作，需 `stickers-write`，返回 `StickerReply.Image`。生成的二维码可通过正常 `PutItem` 或 IMAGE 图层放入图库/拍立得，再 `PublishQuickMedia` 放入快捷文本。生成规则及 Unicode 解码测试由主程序二维码工厂维护，接口不会外传内容或增加网络能力。

## Lua 工具

外置 Lua 编辑器提供 `host.stickers(command)`，合同与 typed SDK 相同。Lua manifest 必须声明 `stickers-read` / `stickers-write` 的实际权限；快图互转再次检查快捷文本权限。源资源只可来自本项目 `data/` 或 `assets/`；副本目的地使用 `exports/` 或 `tmp/`，由运行时映射到该项目独立 data。页面刷新、`contributions/pages/test` 只能执行纯查询，不会导出文件或修改资源。删除确认仍由宿主原生审核页完成。

## 存储和限制

主程序耐久资源与 catalog 位于自身外置 Appdata 的 `appdata/Kigtts/cards`，插件网络、草稿与副本仍在其自身 `plugin/plugindata/<pluginId>`。旧名片的标题、说明和可读取照片会迁移一次；无法读取照片时保留文字，不移除旧偏好数据。

上限：128 图库、2048 贴纸、256 名片、每卡 64 图层、4096 图片、图片合计 2 GiB；最终 catalog（含文字、图层、资源元信息与有限事务回执）UTF-8 总计不超过 8 MiB，超限会在保存或发布前整体拒绝并保留原数据。单图片 32 MiB、最长边 32768、最多 8000 万像素；按解码器验证 JPEG/PNG/WebP/GIF/BMP/HEIF/HEIC/AVIF，实际支持取决于 Android 解码器。导出按限额采样并隔离内存不足错误。

名片和贴纸当前保存、导出静态图片；实况视频和 GIF 动画不会变成动态贴纸。宿主不内置图像生成引擎；Agent 或其它插件可提供已生成的图片资源，再调用正式发布接口。
