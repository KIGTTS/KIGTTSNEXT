# 助手图片和并行会话

0.9.6保留API13，新增以下工具。工具执行审核与插件权限分开：`image_search`、`download_image`只申请一次插件network权限，不为每次搜索另开操作审批。

| 工具 | 输入与结果 |
| --- | --- |
| image_search | query；返回Commons原图/缩略图URL、作者、来源和许可，未下载不算已保存 |
| download_image | url，可选path、view；原图保存workspace，32MiB上限，可选附带视觉图 |
| read_quick_image | mediaId，可选网页内相对path；普通媒体使用空path，复制为助手文件并附视觉图 |
| write_quick_image | 已有条目id、revision、path；保留标题、字幕、分组等字段并按当前模式批准，冲突需重读 |

新图片词条可先publish-media，再在原生快捷文本事务中关联返回的mediaId。网页素材使用下载后的本地文件打包。原图与发送给模型的压缩预览分开保存，不把Base64写入工具文本或Binder页面数据。

同一批tool回执全部完成后才能追加图片user消息。出站兼容旧的交错历史，补全缺失回执为“结果未知”，不重放修改。丢失文件保留文字记录，避免后续每轮都构造失败。服务明确不支持视觉时只对当前供应商/地址/模型停用图片输入，提示切换视觉模型；不宣称已经看过图片，记录与原文件仍保留。

每个会话独占runner、HTTP连接、历史读取闭包、队列与压缩器；审批按conversationId存储。会话切换不会停止后台会话，停止与插队只操作目标会话。原生动作携带_chatConversation，避免切换后的异步动作落到其它会话。空白会话连续点击新建会复用。并行执行的UI通知统一合并到每100ms最多一次，数据与最终状态仍各自保存。

交互参考官方[ZCode仓库](https://github.com/zai-org/ZCode)与[DeepSeek Harness仓库](https://github.com/deepseek-ai/deepseek-harness)：将会话记录、执行状态、工具回执和UI呈现分开。此处为原生宿主与独立插件的实现，无复制其界面代码，无子代理。

本批按用户要求仅编译和安装，未运行新增自动测试或真机交互验收。
