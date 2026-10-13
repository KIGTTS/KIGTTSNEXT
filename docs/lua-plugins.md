# 独立插件编辑器与 Lua 工具

插件编辑器是独立的 `runtime.lua` API9/MIT 插件，发布为 `runtime.lua-0.8.0.kigplugin`。
LuaJ 3.0.1 与编辑器实现只存在于外置包，主程序提供原生页面、文件列表和能力接口。
`.kigplugin` 仍是需要发行者签名的 Android DEX 插件；`.kiglua` 是由此编辑器管理的 Lua 源项目，不是 APK。

## 源包格式

`.kiglua` 是 ZIP，根目录含 `manifest.json`、`main.lua`，可选 `assets/` 目录。
Agent 可以把相同目录通过 `AppCommand.DeliverResource("runtime.lua", resource, "import")` 交给编辑器。
目录或 ZIP 会进入编辑器自己的 Appdata，用户检查代码与权限后点击“安装并启用”才执行。
导入、语法检查和检查页面不会执行 Lua。更新保留数据，权限变化同样需要重新确认。
自动化只能调用明确导出的 `import`，不能调用确认、启用、删除或脚本动作。

```json
{
  "id": "example.tools",
  "name": "我的工具",
  "version": "0.1.0",
  "permissions": ["quick-text-read", "quick-text-write"]
}
```

ID 为小写字母开头，只含小写字母、数字、点或短横线，最长 64 字符；名称 1–64 字符，版本 1–32 字符。
权限仅支持 `app-control`、`quick-text-read`、`quick-text-write`，默认空。
源代码为 UTF-8，最大 256 KiB，不接受字节码。整个项目最多 128 个文件/目录、16 MiB，单项资源最大 8 MiB。
禁止绝对路径、反斜线、`..`、外部链接和大小写重复文件名。根目录不能夹带其它可执行资源。

全部可变资源位于：

```
appdata/Kigtts/plugin/plugindata/runtime.lua/
  projects/<id>/code/{manifest.json, main.lua, assets/...}
  projects/<id>/data/...       # 脚本各自的数据，更新时保留
  drafts/<uuid>/...           # 编辑草稿，不改变当前运行代码
  staging/<uuid>/...          # 等待用户检查的版本
  imports/...                # 系统文件选择器导入
  inbox/...                  # Agent 或其它插件交付
  exports/<id>.kiglua
```

## 编辑和文件管理

“插件编辑器”提供项目列表、启停、导入、导出和测试。打开项目的“项目文件”可以浏览目录、多选、批量导入资源、移动、重命名、新建文件夹/文本文件及删除。
删除工具或文件都有二次确认。`main.lua` 和 `manifest.json` 受保护，不能移动、重命名或删除。
资产只能放入 `assets/`，移动先检查全部目标，目标冲突时保持原文件。
文件列表由宿主通用原生组件显示，多选状态保留行高，视觉、按压和转场使用宿主主题。

文本文件进入独立的二级编辑页面，复用原生源码编辑器的行号与键盘快捷栏。
支持检查 Lua 语法；较大的源文件可导出到外部编辑器修改再导入。
当前原生源码组件最多展示 64,000 字符，不把较大文件默默截断写回。
“检查并保存”产生新审核版本；确认前当前运行模块保持原代码，放弃编辑则删除草稿。
项目数据与资源导出不包含 `data/`，避免把运行过程中保存的隐私数据混进源码包。

## Lua 合同

入口返回一个表。`contributions(ctx)`、`pages(ctx)`、`execute(action, ctx)` 可以返回 Lua 数据表或 JSON 字符串。
这三类返回值只能含 JSON 数据，不能包含函数、userdata、循环表或无限 UI 树。
每次调用使用新的全局环境；跨调用状态使用 `host.data.read/write`。
页面和按钮先缓存，宿主绘制时不执行 Lua，也不重播整个条目列表的入场动画。

```lua
return {
  contributions = function(ctx)
    return {
      actions = {{
        id = "hello", label = "问好", slot = "EDITOR_TOP_END",
        icon = "INFO", buttonStyle = "ICON_TEXT"
      }},
      drawerEntries = {{
        id = "tools", label = "我的工具", pageId = "main", icon = "SETTINGS"
      }}
    }
  end,
  pages = function(ctx)
    return {{
      id = "main", title = "我的工具", blocks = {
        {type = "group", title = "工具", children = {
          {type = "text", text = "使用主程序的原生页面样式"},
          {type = "actions", actions = {{id = "hello", label = "问好", icon = "TEXT"}}}
        }}
      }
    }}
  end,
  execute = function(action, ctx)
    if action == "hello" then
      return {type = "replace-text", text = "你好！"}
    end
    return {type = "refresh"}
  end,
  test = function(ctx) return {ok = true} end
}
```

例中的 `replace-text` 需在 manifest 加上 `app-control`。本地动作和页面 ID 只含字母、数字、下划线、点或短横线，最长 48 字符。
运行时将 ID 转成 `module/<manifest.id>/<id>`，避免不同工具碰撞；Lua 收到的仍是本地动作 ID。
按钮 slot、icon、presentation、buttonStyle 使用 SDK 枚举。菜单按钮的 slot 必须为 `EDITOR_MENU`，presentation 必须为 `MENU_ITEM`。
按钮可用 `ICON` 或 `ICON_TEXT`，空间压缩与相邻控件圆角由宿主负责。

`contributions.settingsEntries` 可将工具放入原生设置卡片，并指定自己的卡片。例如：

```lua
settingsEntries = {{
  id="preferences", label="工具设置", pageId="main", icon="SETTINGS",
  cardId="my-tools", cardTitle="我的工具", summary="调整工具选项", order=10
}}
```

入口 `id/pageId` 自动加 `module/<工具ID>/` 前缀，不同工具可以使用相同本地 ID。
`cardId` 保留原值，以便有意共用同一设置卡片；只允许小写字母开头的小写字母、数字、点、短横线，最长 64 字符。
省略时归入“功能与扩展”。宿主保留卡片的标题和顺序，用户定义的其它卡片使用 `cardTitle/order`。
每工具最多 2 个设置入口，必须指向该工具实际提供的页面，所有工具最多 60 个设置入口。
Lua 可从 `ctx.locale` 读取当前语言并返回本地化文案；此接口不等于内置内容已经全部翻译。

页面块支持 `text`、`notice`、`actions`、`group`、`input`、`toggle`、`choice`。
输入块含 `title/value/actionId/secret/multiline/icon`；开关含 `title/checked/actionId/summary/icon`；选择器含 `title/options/selected/icon`。
输入的动作上下文 `ctx.inputs.value` 是编辑后的值，原生选择器 option ID 也会加模块前缀。
多文件 Lua 代码可放入已审核的 `assets/lua/`，使用 `host.module("util.math")` 加载 `assets/lua/util/math.lua`。
它与主入口共用指令预算，禁止循环引用、任意文件路径、数据目录代码和字节码；不开放通用 `require`。
每工具最多 12 个主界面动作、2 个页面入口和 2 个页面，所有启用工具累计最多 32 个脚本页面。
返回操作支持 `message`、`dialog`、`menu`、`open-page`、`replace-text`、`refresh`。

## 能力接口

`ctx` 是纯数据 `PluginContext`，包含当前字幕、编辑文字、播放/识别状态和本次输入。
`host.app(command)` 与 `host.quick(command)` 的请求/响应 JSON 与 SDK `AppCommand` / `QuickTextCommand` / replies 相同。
不把 Android Context、Java 对象、反射或文件系统路径作为 Lua 对象提供。

```lua
local state = host.app({type = "state"})
local result = host.app({type = "text", text = "新字幕"})
local library = host.quick({type = "query", limit = 50})
host.data.write("settings.json", '{"enabled":true}')
local saved = host.data.read("settings.json")
local html = host.asset.read("index.html")
```

`host.app` 需 `app-control`。`plugin-action` 和 `deliver-resource` 不对 Lua 开放，避免运行脚本绕过安装审核。
`host.quick` 读取需 `quick-text-read`，写入/资源发布/导入导出需 `quick-text-write`；事务继续使用 prepare→review→commit，宿主确认不会被跳过。

`host.stickers(command)` 使用 SDK `StickerCommand` / `StickerReply`，提供图库、贴纸与拍立得名片读写、排序、图片副本及快捷文本图片双向转换。manifest 需声明 `stickers-read` / `stickers-write`；涉及快捷文本图片还需对应 `quick-text-read/write`。资源只允许本项目 `data/`、`assets/`，副本使用 `exports/` 或 `tmp/`（运行时转入本项目独立 data）。页面刷新只能查询；删除仍需原生审核。完整字段与限制见 [图库与名片接口](stickers-api.md)。
资源引用 `source/root.path` 仅接受 `data/...` 或 `assets/...`，不接受其它插件 owner。
运行时转换到当前工具的实际路径；导出和 get-file 的 destination 自动写入本工具 `data/`。
`host.data.read/write` 是每工具独立的文本存储，单项 64 KiB、最多 128 文件、总共 4 MiB。
`host.asset.read` 只读自身资产文本，单次 256 KiB。网页与媒体可通过正式 QuickTextBroker 发布，不提供直接 WebView 或未经审核的宿主回调。
`contributions/pages/test` 的 host 操作只读；实际写入仅在明确点击的 `execute` 中可用。

## 运行约束与验证

LuaJ 使用 Lua 5.2 语言；保留受限基础、数学、字符串、表和 bit32 功能。
不加载可访问操作系统/Java的 io、os、luajava、包加载和协程；debug 仅由运行时用于预算检查，对脚本不可见。
禁用 load/loadfile/dofile、元表访问、pcall/xpcall、字节码 dump 和难以抢占的格式化/模式/大量重复字符串操作。
字符串 find 仅进行字面搜索。每次最多 250,000 指令、2 秒 CPU 窗口、64 层调用、16 次宿主请求；每次宿主请求最长 5 秒。
停止插件会取消执行；无限循环、指数字符串增长、错误或非法 UI 会隔离到对应工具，保持其它工具可用。

这是一套源脚本能力和执行预算限制，**不是操作系统级沙盒，也不提供完整的 JVM 内存配额**。
可信 `.kigplugin` 原生代码仍以应用 UID 运行；导入不可信源代码仍需要用户检查。
引用官方 [LuaJ 源码与 Android 说明](https://github.com/luaj/luaj)、[受限环境示例](https://github.com/luaj/luaj/blob/master/examples/jse/SampleSandboxed.java)、[DebugLib 指令回调](https://luaj.org/luaj/3.0/api/org/luaj/vm2/lib/DebugLib.html)。

本轮测试覆盖确认前不运行、远程确认门禁、无限循环/字符串增长/取消、禁库、权限与只读页面、哈希校验、更新数据保留、坏 ZIP/字节码/路径、JSON 类型、文件移动与草稿隔离。
实体手机测试按用户安排推迟，不声明已在手机安装或验证。
