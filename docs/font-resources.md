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

## 可变字重

选中带 `wght` 轴的字体后，字体列表显示“字重”和“恢复默认”。滑条使用字体文件自己的范围，按 10 调节并限制端点；默认值也来自文件。静态字体不展示滑条，原有字体配置缺少字重字段时使用 400。字重随外观设置保存，作用于界面、字幕与预览；标题与粗体保留相对字重层级。

宿主只读取有边界检查的 sfnt/fvar 记录，字体构造与 Compose 预加载在 IO 线程完成。更新时保留已显示的字形，沿用可见字高规范化。字体列表预览使用独立文字规格，避免重复叠加主题字高。

2026-10-27 字体源新增 9 款，总计 11 款：Noto Sans 简体中文、马善政毛笔楷书、Noto Serif 简体中文、站酷小薇体、站酷快乐体、站酷庆科黄油体、龙藏手书、Nunito、Roboto、JetBrains Mono、Noto Sans 日文。6 款可变字体提供源文件轴范围；每包保留其上游 OFL 原文。已发布的字体字节、版本和签名包保持不变。

字体条目图标由实际字体的字形轮廓生成 SVG：中文“永”、英文“Aa”、日文“あ”，不依赖手机已有字体。图标保留统一浅色圆角底和深色字形，预览图继续使用各字体样张。上游提交及字体/许可哈希固定于 `distribution/font-sources.json`。

发布器已上传并核对全部 69 个公开文件（字体、许可、轮廓预览、字形图标、签名资源包与目录）的 SHA256。11 个包的签名、载荷及许可均再次校验通过，图标已渲染检查。公开下载校验记录位于 `.tools/font-source-verified.json`。

2026-10-29：字体图标改为无背景的真实字形 SVG，并通过 icon.monochrome 注册为主题色图标。字重调整改为单独弹窗，字体列表使用分段列表与侧滑操作。此次只更新图标素材与目录，已发布字体包保持不变。

2026-10-30：从老 KIGTTS 字体下载源迁入 12 款 OFL 字体，总计 23 款：思源黑体 CN、思源宋体 CN、得意黑、霞鹜文楷 GB 屏幕阅读版、缝合像素字体 12px 简体、小赖字体、荆南缘默体、荆南麦圆体、霞鹜漫黑、霞鹜臻楷 GB、悠哉字体、铁蒺藜体。思源两款保留 250–900 可变字重；悠哉本次收录原版 Regular。未修改字体或授权文件，不重复已有字体，不涉及旧时钟字体。

迁移来源、版本、原始字体/授权 SHA256 和相对文件路径记录于 `distribution/font-sources.json`。首次迁入可指定旧源导出目录；发布后，构建器可从新字体源的镜像重建。路径限制与哈希校验同样适用于本地输入，不能用另一份字体或许可证替换。

```powershell
python tools/Build-FontSource.py --legacy-folder <旧字体资源目录>
python tools/Build-FontKpmSource.py --private-key <本地签名私钥路径>
python tools/Verify-FontSource.py
python -m unittest discover -s tools/tests
python tools/Publish-FontSource.py
```

验证工具检查每包签名、发布者、所有载荷哈希、OFL 原字节、可变轴及主题色轮廓图标，并生成字形联系表。签名字体包已存在时，构建器逐字节核对且拒绝覆盖不同内容的同版本包。
