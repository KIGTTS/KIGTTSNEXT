# Android 更新检查

魔搭的正式更新公告文件位于 `LHTSTUDIO/KIGTTS-Official-Plugins` 数据集的 `android/latest.json`。仓库内的 [公告文件](../distribution/app-update.json) 与线上使用相同格式。正式发布时，将 `release` 设为包含三段式 `version` 和纯文本 `notes` 的对象，并把更新后的文件同步到魔搭。

准备阶段的 `release: null` 会让客户端继续检查 [GitHub 最新正式 Release](https://api.github.com/repos/KIGTTS/KIGTTSNEXT/releases/latest)。魔搭超时、HTTP 错误或返回无效公告时也使用这个后备源。GitHub 版本来自 `tag_name`，更新说明来自 `body`。

更新按钮打开 KIGTTS 官网。应用依据已安装的三段式版本判断是否更新。仅重打相同版本时无需更新公告。
