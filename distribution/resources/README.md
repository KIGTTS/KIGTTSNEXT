# KPM 资源包

资源包可包含快捷文本、照片集、滤镜、语音包、照片边框或贴纸包。添加后由相应资料库管理，不会创建插件条目。快捷文本每次添加都会生成新的条目与分组。

`classic-frames/` 是一个可直接打包的原创相框项目，按 CC0 1.0 提供。

```powershell
python tools/Build-ResourcePackage.py distribution/resources/classic-frames --output build/resource-packages/classic-frames-1.0.0.kigresource --private-key <私钥DER路径>
```

打包器返回源目录需要的精确哈希、大小与发布者。为其补充 HTTPS `mirrors` 后放入 `schemaVersion: 2, kind: RESOURCES` 的源目录。包项填写 `resourceType`；发布格式为 `RESOURCE`、`apiVersion: 29`。详见 SDK 的 [资源协议](../../.sdk/docs/resource-packages.md)。

原生快捷文本备份填入 `payload`（`.kigcards`）；语音包填入 `.kigvpk`，Piper 语音包同时声明 `service.speech-synthesis` 与 `engine.piper` 的实际前置依赖。LUT 使用 `filters: [{path: "filters/sample.cube"}]`。相框用 `frames`，照片集和贴纸包用 `snapshot`，本地图像用 `imageFiles` 映射资源 ID 与相对路径。

当前导入 PNG/JPEG/WebP 等位图，图层与图像映射保留后续混合 SVG 的扩展位置。不要把 SVG 当作已支持的照片数据发布。

API30 新增 `card-borders/` 名片边框与 `word-stickers/` 参数字贴原创示例。名片边框安装到独立名片资源库，字贴可在选中后通过齿轮调整文字/颜色/字号。字体可随参数包嵌入，参见 SDK 协议。

`info-stickers/` 收录 14 种原创信息贴纸，API34 在放置时默认填入当前相片的拍摄信息，并保留手动编辑。字体使用重命名的 OFL 常用字子集，原始许可与来源哈希一并保留。可用 `python tools/Build-InfoStickerPack.py` 重建素材和商店预览。

`remi-stickers/` 按作者文档收录 22 张 1024px PNG，原始像素、透明度与 SHA256 保留于 `origins.json`。Remi 图像权利归原作者，不适用原创资源的 CC0 或字体 OFL。

源条目、介绍、预览尺寸与各包许可集中在 `distribution/resource-projects.json`。构建器保留已发布的版本和哈希，不能用同版本替换其它字节。
