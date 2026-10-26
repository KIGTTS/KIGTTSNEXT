# 字体资源

API32 将字体作为 KPM 的 `FONT` 资源。每包一个 TTF/OTF，通过 `content.json.payload` 引用，随包保留原字体许可；文件上限 40 MB。

宿主核对发布者、签名和载荷哈希后，再验证并复制字体到外观素材库。相同文件按内容去重；添加不会切换当前字体，也不注册插件。用户在“设置 → 外观 → 字体 → 我的字体”中选择，继续使用原有字高规范化。

字体页保留本地导入；商店处于可用状态时展示“从商店获取”。首次进入先确认字体来源，之后直接进入字体分类。

独立字体源地址：

<https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Fonts/resolve/master/kpm/index.json?view=false>

旧字体目录 `index.json` 保留，旧版本仍可使用。KPM 预览使用源中的 SVG 轮廓字形，浅底深字，亮暗模式均可读。构建统一 LF，以公开下载返回的字节核对 SHA256。

```powershell
python tools/Build-FontSource.py
python tools/Build-FontKpmSource.py --private-key <本地签名私钥路径>
python tools/Publish-FontSource.py
```

第二步也可直接使用已经缓存且校验通过的旧字体目录，不必重新下载上游字体。发布器先上传素材与签名包，再更新 KPM 和旧目录，并逐文件验证公开下载。

回归覆盖签名导入、重复添加去重、保持当前字体、无插件管理条目、损坏字体拒绝、旧 API 拒绝，以及商店入口显示条件。实机操作验收由用户进行。
