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

## 0.9.11 本地图像处理

`image_edit`读取助手自己workspace/imports的真实位图，生成新的PNG；支持像素裁剪、90°旋转/镜像、连通种子填色、亮度/对比度/饱和度、边缘连通纯色抠图。抠图是颜色容差算法，不是人物语义分割；不会自动上传照片。单图最多800万像素，先用`resize_image`采样缩放更大的原图。工具附实际结果图片，复杂背景应如实说明限制。

`image_edit(operation=vectorize)`将已有透明图片的alpha轮廓转为单色SVG，保留透明孔洞；不是多色照片矢量重建。`svg_edit`可改viewBox裁剪或调整填色/描边，并通过宿主返回真实PNG预览。SVG拒绝DOCTYPE、实体、脚本、外部图像/样式引用。生成文件都在workspace，指定destination必须不存在；原图始终保留。

示例：先`read_quick_image(mediaId)`复制用户指定图，再`image_edit(path,operation=remove_background,tolerance=24)`，检查返回透明边缘；需要贴纸时把该真实文件交给既有stickers导入接口。无需额外操作审批的本地副本处理，与修改宿主卡片的审核分别处理。

模拟浏览器`devices`读取当前设备的物理像素、逻辑尺寸和DPR；`device=current`、`orientation=portrait/landscape`或显式width/height控制验收尺寸。尺寸为CSS逻辑像素，截图像素与触点坐标需按回执映射。模拟截图对真实WebGL帧读回，不为截图伪造占位画面。原生主界面和模拟注入同一套安全区变量及其兼容别名。

工具参数解码前错误返回`INVALID_ARGUMENTS/executed=false`。完全访问中的结果不明确会返回模型核对原状态/事务，暂停同批依赖操作，不自动重放已有变更。历史查询排除自身回声；需要旧完整源码时使用当前会话的`recover_file(callId,path)`，不能从截断片段拼接或扫描其它会话。API缓存统计只展示供应商真实usage；请求保留稳定前缀，压缩按模型容量预算，不伪造命中率。

## 0.9.12 兼容性补充

分组图标可使用宿主离线Material Symbols目录中的真实名称，通过icon_catalog查询后写入symbol:名称。SVG编辑支持Android解析器，不依赖桌面Xerces扩展；原稿与生成文件分别保留，渲染失败明确返回ok=false、saved=true及具体预览错误，不把仅保存成功当作视觉验收通过。
