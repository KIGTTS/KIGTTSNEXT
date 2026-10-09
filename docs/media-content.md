# 大字幕媒体与内容编辑

大字幕支持文本、图片、Android 动态照片、视频、HTML 和 HTTPS 网页。打开大字幕左上角文本菜单，选择“选择图片、视频或网页”进入内置媒体库；从 Android 图片选择器或文件选择器导入后，点击媒体使用。图片可双指缩放、平移、双击恢复；长按图片或点击“全屏”展开，返回恢复主页。

图片、视频及实况片段可在同一文本菜单中选择“适应”或“填充”，每个媒体单独记住选择，旧记录默认适应。适应完整保留内容比例，空白区域使用相同内容的高斯模糊画面铺满整张卡片（包括四角按钮背后），四角按钮浮在媒体上方；填充按整张大字幕卡片比例裁切。静态图片 Android12+ 使用 GPU RenderEffect，并延伸边缘颜色；API26–30 在后台一次性处理 ≤128 px 小图。视频只使用一个播放器/解码器，GPU 对同一帧进行 ≤128 px 双向高斯与原图合成，再按整卡片比例输出；播放中不截图、不进行 CPU 像素读回或高斯处理，填充模式跳过背景计算，切换模式不重建播放器。

切换媒体或恢复文字时，旧媒体淡出并轻微收缩，退场结束后释放；文字也同步渐入。即使删除原文件，已解码图片仍保留到退场完成。退场内容立即停止播放、输入与网页动作；旧网页不再覆盖新内容的历史或编辑状态。媒体库/编辑器使用沉浸的 Large Flexible 顶部栏：导入/文件与更多操作、保存/查找分别位于各页顶部，键盘出现时顶部栏缓动收起，返回遵循原页面层级。

## 实况照片

Android 动态照片默认可用。导入图片时读取 Google Motion Photo 的 Camera / Container Item XMP 元数据，兼容现代 `MotionPhoto` / `Item:Length` 与旧 `MicroVideoOffset`，验证尾部为 ISO-BMFF 视频后提取。现代长度优先于旧偏移；关闭标记、损坏 XML、越界偏移、非法视频头均按静态图片处理。图片保留原文件，动态片段点击“播放实况”后播放一次，结束恢复照片，默认静音。静态图片始终保留在视频下方，首帧准备好才渐入视频，结束/退出渐隐后释放；同一播放胶囊形变为控制栏。JPEG、HEIC/HEIF/AVIF 的静态解码及视频编码支持取决于设备解码器；非标准厂商私有打包格式尚未承诺兼容。

“播放实况”入口保留紧凑的文字胶囊：视觉高度 40 dp，宽度随文字和系统字体大小适配。按住时圆角与尺寸联动形变，取消按压恢复且不触发播放；展开时高度增至 48 dp、宽度扩展，播放图标与进度/声音/返回照片控件同步缩放和淡入。展开、收起均使用轻微回弹的弹簧动效，回弹范围限制在卡片内；页面进入和返回仍使用原有非弹簧动效。

**苹果实况照片默认关闭。** 在“设置 → 实验性功能 → 苹果实况照片”开启后，媒体库中普通照片的管理菜单出现“配对苹果实况视频”，选择导出的对应视频。支持照片＋视频的人工配对；不直接读取 Apple Photos 数据库、不自动校验 Apple 资源标识。关闭开关后配对入口和动态播放按钮消失，已导入照片及关联保留，继续显示静态画面。Android 动态照片不受该开关影响。插件声明 `appleLive=true` 的配对图片也遵循该开关。

## 快捷条目与内置编辑器

编辑快捷条目，在详情弹窗内选择媒体；名称、说明、分组和媒体关联保存到原条目，排序及稳定 ID 保持。进入选择器/编辑器时暂时隐藏详情弹窗，返回后恢复草稿。点击媒体条目会显示对应媒体；普通文本条目继续填入文字。删除媒体需确认，引用它的条目保留文字并移除关联；删除条目不删除媒体文件。

“文本编辑器”和“HTML 编辑器”为正常二级页面，支持保存、撤销/重做、查找替换；有修改时返回须确认放弃。文本最大 128 KiB，HTML 最大 2 MiB，撤销最多 20 项且限制总字符内存。文本编辑器保存后回到文本显示；HTML 保存到媒体库。媒体库内可编辑已有宿主 HTML；插件 HTML 的编辑会保存为新的宿主副本。HTML/网页内容交互时不会触发大字幕的文本输入手势，网页输入键盘出现时主页其它区域收起。

用户导入内容为持久副本，存于应用外部专属目录 `appdata/Kigtts/media/<id>/`；媒体目录不要求广泛存储权限。视频最大 2 GiB，HTML 最大 2 MiB，取消系统选择器不会改变已有内容。图片解码完成、网页加载完成后淡入并轻微缩放。视频播放提供播放/暂停、MD3E 波浪进度和静音，进度仅在播放时按帧采样，拖动释放后才提交定位；波浪使用 Material 颜色/笔宽/波长，并独立保存累计相位，暂停/结束时移动速度与波幅一起缓动至零，轨道平滑收成直线；继续播放时从保留相位平滑恢复波浪，不使用任务进度的幅度变化规则。页面进入后台或被其它页面遮挡时暂停，内容移除时释放播放器。视频使用 TextureView，跟随卡片裁切。

## 插件媒体接口（API6）

插件动作返回 `PluginOutcome.PresentContent(SubtitleContent...)`：

```kotlin
PluginOutcome.PresentContent(SubtitleContent.Image(
    PluginResource("resources/photo.jpg"), description = "图片"))
PluginOutcome.PresentContent(SubtitleContent.Video(
    PluginResource("resources/video.mp4")))
PluginOutcome.PresentContent(SubtitleContent.Html(
    source = PluginResource("resources/page/index.html"),
    description = "插件界面", allowedActions = listOf("save")))
```

资源必须位于呈现插件的 `appdata/Kigtts/plugin/plugindata/<pluginId>/`；不允许呈现其它插件的资源。插件图片也自动检测 Android 动态照片，提取片段仍存于本插件的 `resources/motion/`。`Image.motionVideo` 可显式指定配对视频，`Image.appleLive` 标记苹果配对。`Video` 使用本地资源，`Web` 使用 HTTPS 地址；`Html` 可使用文件或内联字符串（UTF-8 ≤128 KiB），更大的 HTML 用资源文件（≤2 MiB）。`PluginContext.contentKind` 为 `TEXT` / `IMAGE` / `MOTION_PHOTO` / `VIDEO` / `HTML` / `WEB`，`contentId` 为当前媒体引用；媒体模式中的 `editorText` 是保留的文本，不代表当前显示内容。

本地插件 HTML 可以调用：

```javascript
Kigtts.postMessage(JSON.stringify({action: "save", inputs: {value: "内容"}}));
```

宿主只接受自身本地来源的主框架消息，动作必须出现在 `allowedActions`（最多 32 个），输入最多 32 个字符串字段，单个值 ≤4096 字符，总消息 ≤64 KiB。宿主按插件 ID 调用原有 `execute(actionId, context.inputs)`；不暴露 Activity 或任意 Android 接口。系统 WebView 必须支持 `WEB_MESSAGE_LISTENER`，否则显示更新提示。远程网页和用户导入 HTML 不拥有插件动作桥。调试插件配置页提供“显示插件网页示例”。

本地页面通过 HTTPS 虚拟来源加载，仅可读取当前 HTML 所在目录及其子目录；禁用 `file://`、内容 URI、混合内容、持久 Cookie 和 DOM 存储。远程资源只允许 HTTPS GET，显式拒绝非 HTTPS 重定向及外部 Intent；POST、登录 Cookie 和持久站点存储当前不支持。插件使用 `Web` 或 `Html.allowNetwork=true` 时须声明 `network` 能力。网络响应写入所属插件 `cache/web/<contentId>/`，宿主网页写入 `appdata/Kigtts/media/web-cache/<id>/`；每个资源最多 32 MiB、每个内容缓存最多 128 MiB。此加载器针对可展示及可扩展的网页内容，不能替代完整浏览器的兼容能力。

API6 改变 `PluginContext` 二进制结构，API5 及更早的 DEX 包须同签名重建为 0.5.0/API6；升级保留模型、配置和外置资源。主程序仍不包含语音引擎或语音服务实现。

## 验证边界

本地测试覆盖元数据现代/旧格式、恶意/损坏输入、媒体持久化与资源归属、旧条目迁移及排序、设置默认关闭与重启、苹果播放门控、选择器草稿返回、图片实际解码/全屏、文本保存/未保存确认和协议序列化、网页来源与动作校验。原生媒体 UI 截图由 Robolectric Native Graphics 生成。

`MediaDeviceTest` 的三个仪器用例仍按用户选择暂缓测试 APK 安装，未宣称运行通过。独立管理器现已完成六个插件的 API6/0.5.0 更新，及 Android 动态照片导入/静态显示、原生 H.264 GPU 首帧/正确比例/整卡片高斯背景检查。新增本地截图覆盖胶囊紧凑尺寸/按压取消/弹性展开收回、删除媒体后的退场像素保留和文字渐入、波浪暂停中间帧到直线及继续恢复；相位与拖动释放一次定位也有回归。完整实况首尾切换的逐帧黑闪统计、不同品牌动态照片、真实 HEIC/MOV、网页输入法/插件消息/网络页面与长期设备性能仍待进一步硬件验收。

格式依据：[Android Motion Photo 格式](https://developer.android.com/media/platform/motion-photo-format)、[Apple 配对视频资源](https://developer.apple.com/documentation/photos/phassetresourcetype/pairedvideo)。组件依据：[Media3](https://developer.android.com/jetpack/androidx/releases/media3)、[WebView 消息监听器](https://developer.android.com/reference/androidx/webkit/WebViewCompat)。
