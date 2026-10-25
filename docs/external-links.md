# 网页与 NFC 入口

## 链接格式

`kigtts://open?url=https%3A%2F%2Flhtstudio.com%2Fevent&mode=custom-tab&add=false&title=活动`

- `mode=quick-text`：在字幕板打开网页；`custom-tab`：由浏览器 Custom Tabs 打开。
- `add=true`：确认后保存到当前快捷文本分组。保存后的条目会记住打开方式。
- `kigtts://quick-text?text=你好&title=问候&add=true`：确认后显示或保存文字。每个查询值都应使用 `encodeURIComponent` 编码。
- 官网也可以使用 `https://lhtstudio.com/kigtts/open?...` 或实际子域名上的相同路径；`/kigtts/quick-text` 同样有效。
- NFC 标签可写入以上 URI。为兼容 Android 各版本，活动标签优先使用 `kigtts://` URI；Android 16 的 HTTPS NFC 标签按 ACTION_VIEW 路由。

网址仅接受 HTTPS；精确域名 `lhtstudio.com` 及其子域名受信任，非标准端口、相似拼写、用户名伪装等不会获得信任。其他网站在打开前显示第三方提示，保存始终需要确认。不会从链接执行插件操作或导入任意本地文件。

## 插件入口

SDK API29：声明 `network` 后调用 `environment.appBroker.request(AppCommand.OpenWeb(url, title, mode, addToQuickText))`。返回 accepted/pending 表示交给用户界面处理，不能据此声称已打开或保存。快捷文本网页仍遵循条目联网权限。

## 官网关联

HTTPS 自动唤起需要网站在 `/.well-known/assetlinks.json` 提供当前应用包名与签名证书指纹，且 HTTPS 不发生重定向。AndroidManifest 已声明官网路径；这不代替网站部署。请使用实际分发证书，不要复用示例或别的应用指纹。自定义 scheme 不依赖网站关联。

拍立得、活动抽奖等后续动作应新增具备独立预览和确认流程的版本化路由，不能通过任意 destination 参数访问内部页面。

参考：[Custom Tabs](https://developer.chrome.com/docs/android/custom-tabs/guide-get-started)、[NFC 路由](https://developer.android.com/develop/connectivity/nfc/nfc)。
