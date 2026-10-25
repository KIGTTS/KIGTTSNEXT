# 插件商店与包源

`tools.package-store` 是独立 MIT 插件，版本 0.11.0，要求 API23。默认内置一条魔搭官方源，但不自动信任发布者或安装插件。设置中提供一个「插件商店」入口，使用主程序同一套分组、圆角、按钮与页面动效。源管理和浏览视图共用数据模型；可以浏览全部源，也可切到指定源。

## 使用流程

添加 HTTPS 目录地址，确认后更新包列表。一个源可以有多个包，每个包保留多个版本，每个版本支持多个下载镜像。支持搜索、已安装/可更新筛选、源启用与排序、版本选择、检查更新以及清除下载缓存。源内条目缓存会在插件启动时预加载，每次进入商店首页立即显示缓存，同时后台更新已启用源；刷新中重复进入合并，下载等任务繁忙时延后刷新，旧目录在网络失败时继续可用并显示更新错误。

`kigtts://source/add?url=<encoded HTTPS>` 只提出添加源的请求，进入确认页；不会立即访问网络、添加信任或安装。源地址与源 ID 都必须唯一，同一已添加 URL 返回不同 ID 时需要重新人工确认。

选择一个版本后，商店会查询主程序真实已安装状态，解析完整前置依赖，列出安装计划，然后按依赖优先顺序下载和校验。兼容的已安装版本优先复用。可选择较早版本，但不会悄悄降级；主程序的版本替换与发布者/权限审核必须由用户确认。

相同包 ID 来自不同发布者、相同版本返回不同内容、范围冲突、缺包、循环或超长依赖链都会停止。若变更会破坏其它已安装包的已知依赖范围，也会要求调整版本或源。未知已安装包的依赖信息不会被编造；主程序实际插件依赖仍做最终检查。

原生 `.kigplugin` 和独立 Lua `.kiglua` 是当前支持的两类包。原生前置链交给主程序一次检查与确认。Lua 先完成所需原生包安装，再把已经验签的源文件送到 Lua 运行时审核；只有其已批准项目出现在主程序 `managedPackages` 元数据中，商店才继续下一项。提交安装或 `accepted/pending` 不等于已安装，取消不会被自动重试。

## 源格式

[发布源指南](https://github.com/LHT02/KIGTTS-PLUGIN-TEMPLATES/blob/main/catalog/README.md)、[完整示例](https://github.com/LHT02/KIGTTS-PLUGIN-TEMPLATES/blob/main/catalog/package-source.json) 与 [JSON Schema](https://github.com/LHT02/KIGTTS-PLUGIN-TEMPLATES/blob/main/catalog/package-source.schema.json) 可供人和 AI 直接使用。目录最多 4 MiB、32 个源、每源 500 个包、每包 64 个版本，每版本 1–8 个镜像。

| 字段 | 含义 |
| --- | --- |
| `schemaVersion/id/name/packages` | 目录版本、稳定源 ID、显示名称与包列表 |
| 包的 `id/name/summary/releases` | 必须匹配签名包的 ID、名称、说明与全部历史版本 |
| `version/versionCode/format` | SemVer 名称、签名清单整数版本号，以及 `NATIVE`/`LUA` |
| `sha256/bytes` | 最终下载 ZIP 的 SHA-256 与准确字节数；所有镜像必须一致 |
| `expectedPublisher` | 包内 `signature/public.der` 的 SHA-256，不是发布者名称 |
| `apiVersion/minSdk/abis` | 精确接口版本、最低 Android 版本与可用架构 |
| `dependencies` | 前置包 ID → 版本范围；Lua 必须包括 `runtime.lua` |
| `mirrors` | 按顺序尝试的 HTTPS 下载地址与显示名称 |

范围支持完整三段精确版本、比较符 `>= <= > <`、`^1.2.3`、`~1.2.3`、`1.x/1.2.x/*`、`1.0.0 - 2.0.0` 与 `||`。不接受 `latest` 和不完整的 `^1.2`。预发布不会成为默认最新版本，但可以明确选择；范围中未明确提到预发布时不会自动采用它。依赖解析采用有界回溯，不会因为先选了一个不兼容的最新版本而错过其它满足条件的组合。

Native 清单使用现有 RSA/SHA-256 签名协议。Signed Lua 包新增 `manifest.json.versionCode/files`，并携带 `signature/public.der` 和 `signature/manifest.sig`，签名覆盖原始 UTF-8 清单；`files` 覆盖 `main.lua` 与 `assets/`。商店检查签名、真实发布者、包 ID、版本与各资源 hash，再仅复制清单、源码和 assets 给运行时，不执行脚本。运行时导出的普通 unsigned Lua ZIP 仍可手动导入，不能冒充商店签名包。存在于签名清单中的 dependencies 必须与目录一致；旧签名包没有该字段时，目录的额外依赖会明确展示在用户安装计划中。

## 存储与权限

官方目录为 Piper 声明 `service.speech-synthesis: ^0.8.0`，为流式识别与 SenseVoice 声明 `service.speech-recognition: ^0.8.0`。通过商店安装引擎时，把缺少或版本不匹配的服务加入同一安装计划，先装服务再装引擎。满足版本的已安装服务会复用；当前不会自动启用被用户禁用的服务，手动导入独立原生包也不会自动查源补齐前置。旧签名包未声明依赖时由目录补充，不重写已发布二进制；新签名清单如包含依赖则以签名内容为准。

从网页打开添加源链接时，宿主先等待插件加载完毕。没有商店会提示下载官方插件商店，用户确认后才联网，随后检查大小、SHA-256、包ID、发布者、签名和兼容性，再由原生弹窗确认安装。商店已安装但停用时改为提示启用。源地址跨重启保留，取消不自动下载；商店可用后继续源确认，不提前读取待添加的第三方源。

配置、目录、下载、中途文件与已验签 Lua 投递资源全部位于 `appdata/Kigtts/plugin/plugindata/tools.package-store/` 的 `config/catalogs/downloads/tmp/resources`。每次缓存命中会重新检查 byte count 与 SHA；下载失败或取消会清理本插件中途文件，镜像只在下载阶段切换。HTTPS最多显式跟随5次跳转以兼容魔搭LFS/CDN；每次核验HTTPS与无嵌入帐号，不复制Authorization或Cookie，拒绝HTTP降级，最终仍核对大小、SHA-256和签名。

源的 `expectedPublisher` 只用于一致性校验，不能替用户授权某个发布者。主程序对原生包仍再次验签、显示权限、保存用户发布者信任并处理版本替换；Lua 仍由独立运行时显示权限、限制执行资源并人工审核。API29增加独立资源包导入，见下文；未知 format 不作为可安装代码。

## VCC 调研与区别

参考 [VRChat VPM Repos](https://vcc.docs.vrchat.com/vpm/repos/) 的源/包/多个版本关系、[Packages](https://vcc.docs.vrchat.com/vpm/packages/) 的版本范围与传递依赖，以及 [Package Manager](https://vcc.docs.vrchat.com/vpm/) 和 [CLI](https://vcc.docs.vrchat.com/vpm/cli/) 的明确版本选择。KIGTTS 使用自己的 Android/Lua 包格式与源 JSON，不读取 VPM/Unity 包，不修改 Unity 工程；签名发布者校验、存储所有权和安装审核由本程序独立实现，没有复制 VCC 或 MaterialFiles 的实现代码。

本地回归使用模拟HTTPS传输与现场生成RSA测试包；官方源发布后另做匿名下载与字节/签名核验，实际完成情况见Release验证记录。

## 官方源发布

源地址：`https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Official-Plugins/resolve/master/index.json?view=false`。源码常量为 `OfficialPluginSource`。首次安装/迁移商店补入一次；删除后不反复补回，已有源、缓存和待确认项保留，最多32条。

运行 `tools/Build-OfficialPluginSource.ps1` 从 `distribution/official-packages.json` 的明确列表准备清单、签名包、说明与许可；脚本核对发布者、RSA签名和载荷hash。仅上传输出目录文件，不上传工作区、模型、私钥或用户工程。已发布的版本不可改写；修复增加新版本，并保留旧版下载用于切换。目录/说明MIT不改变GPL插件及依赖许可，README提供对应源码与构建位置。

## 浏览层级（0.10.0）

发现首页标题栏提供「搜索、我的、下载管理、设置」入口；分组标题不放副标题；插件条目使用64dp图标并展示简短介绍，完整Markdown介绍放在详情页。更新入口放到「我的」页。列表以稳定顺序显示，同一插件在多个来源中的重复条目合并为兼容的最新版本，不因点击或安装重排序。我的插件与插件更新为独立页，来源暂不可用的已安装插件仍保留并能进入原生管理。

插件详情先展示用途、安装/更新操作和版本动态；所有版本独立选择，不能点选不兼容版本。来源、插件ID、许可、发布者摘要、接口与镜像信息在「来源与兼容性」二级页。源列表与筛选放到「商店设置 → 内容来源」，清缓存移到「商店设置 → 下载与缓存」。源链接唤起、依赖解析、镜像下载及安装前确认仍沿用原机制。

## KPM 展示与网页入口（API14）

KPM（KIGTTS Package Manager）沿用目录schemaVersion=1，以下展示字段均可选，旧源无需迁移：

| 字段 | 格式 |
| --- | --- |
| 源/包 `icon` | `{ "symbol": "extension", "image": "icon-id" }`；图片优先，symbol回退 |
| 源 `readme` / 包 `description` | 最多32000字符的Markdown，可含表格/列表/链接；HTML不执行 |
| 源 `assets` | 素材ID → `{url, sha256, bytes, alt}`，HTTPS的PNG/JPEG/WebP或静态SVG |
| 包 `thumbnails` | 最多8个已注册素材ID |

每图最多8MiB，每源最多128图/64MiB。更新源时预取并校验素材，坏图不阻止有效目录；进入页面不自动连接介绍中的外部图片。Markdown图片以 `![说明](asset:icon-id)` 引用已注册资源。资源不改变包签名、发布者与权限。

网页指引和独立GitHub Pages模板：[KIGTTS/KPM](https://github.com/KIGTTS/KPM)。网页从同一目录生成，提供浏览、Markdown介绍、二维码、复制源与 `kigtts://source/add?url=` 唤起。唤起后仍由软件确认，源链接不直接安装包。页面无需服务器或账户，不复制Poiyomi的代码或美术资源。

`debug.ui` 已拆为独立可选包，主程序生产依赖不再包含UI调试类；配置位于它自己的插件目录。

## 详情与进入刷新（0.10.1）

详情页展示80dp图标（源图片优先、symbol回退），权限说明默认折叠并按所选版本切换。每个release可选声明`permissions: string[]`和`permissionPurposes: {permission: purpose}`，最多32项、用途600字；缺省表示源尚未提供，空数组才表示没有额外权限。官方生成器从已验证签名清单提取capabilities（排除ui）及用途；下载后再次核对说明与签名包一致，说明不能代替实际授权。页面读取不启动网络；可见进入动作才请求刷新，不自动安装更新包。

## 下拉更新与标题栏进度（0.10.2）

首页、我的、插件更新和插件来源页面支持滚动到顶部后下拉更新全部已启用源。下拉指示器随手指运动，松手后回收；持续更新使用插件通用标题栏加载环，不在列表内插入更新提示或取消按钮，不因刷新禁用浏览操作。重复进入与手动下拉共用合并流程，正在下载时延后刷新；失败保留缓存并允许重试。后台loading与动作繁忙为独立状态，其它插件也可通过API16页面元数据使用。

## 列表内下载与下载管理（0.11.0 / API23）

插件条目右侧保留48dp点击区域：未下载时为“获取/更新”，下载时变为可取消的圆形进度，验签时显示不定进度，完成后变为“安装”。点击获取不会打开详情或安装计划页面；点击条目其余区域仍可查看介绍。依赖与降级在下载前以完整清单弹窗确认，取消后从下载管理继续时仍展示该清单。

下载串行排队，每项任务独立取消和重试；一个待安装任务不会占住整个商店。下载管理显示确认、排队、下载、检查、待安装、已安装和失败状态，可以清理结束记录与安装包缓存。队列随当前商店进程存在，包文件沿用已校验的磁盘缓存。

主程序关闭安装弹窗时重新运行当前插件页声明的进入动作。商店读取实际安装元数据，成功才标记已安装；取消或失败则保留可继续安装状态，不重复提交已装依赖，也不残留旧的全局安装计划。权限仍在首次使用时申请，下载不会授予权限。

重复源链接直接展示已有来源并说明“已经添加过了”，保持原来的启用状态；相同来源使用另一个地址时提示在插件来源中查看。

API23 的 `ManagementList.itemActions` 提供每行 `ManagementProgressAction(label, action, progress, running)`；进度为0–1或空值。旧插件的ManagementList构造和copy签名保留。

## 网页插件详情链接

kigtts://source/add?url=<编码后的HTTPS源地址>&package=<插件ID>。package 可选；省略时只添加源。主程序持久化并把 packageId 交给商店；商店先确认未添加的源，再打开该源内的目标插件。已添加源不重复添加，停用源不会自动开启，链接不自动下载或安装。

## 资源与预览（API29，商店0.15.0）

schemaVersion=2的资源源使用kind=RESOURCES。各包声明resourceType（QUICK_TEXT/PHOTOS/FILTER/VOICE/FRAME/STICKERS），发行format为RESOURCE，下载扩展名为.kigresource。签名、镜像、依赖和哈希规则继续适用。资源经用户确认进入各自资料库，快捷文本可重复导入，不计入插件安装或卸载列表。语音包缺少前置服务时先引导添加。

首页按分类展示少量内容，标题右侧“更多”进入完整渐进列表；资源入口提供独立分类与来源管理。预览素材支持任意原始宽高比，assets可同时提供width和height；原生和网页图库保留比例并可放大，SVG预览也按viewBox比例展示。

原生详情支持最多8张横竖混排预览，每张图片按自身比例确定卡片宽高，并支持横向吸附滚动。未填写尺寸时从位图或SVG读取比例；点击任意预览可进入全屏图库连续翻页，关闭后保留查看位置。

官方插件图库维护在ARTS/PluginStore，包含15款花型SVG/PNG封面与隔离环境的原生界面预览。素材索引记录原始尺寸、哈希和截图来源；Build-OfficialPluginSource调用独立的Add-OfficialPluginArtwork脚本，将素材复制为带哈希的不可变地址，并在各包thumbnails中注册。重新生成与许可说明见ARTS/PluginStore/README.md。

官方资源源：https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Resources/resolve/master/index.json?view=false 。源构建、签名与发布脚本见tools/Build-ResourcePackage.py、Build-ResourceSource.py和Publish-ResourceSource.py；具体内容结构见SDK的docs/resource-packages.md。
