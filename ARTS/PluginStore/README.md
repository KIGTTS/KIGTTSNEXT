# 官方插件封面与预览

15 款插件各有一张 1600 × 800 的 MD3E 花型封面，提供 SVG 和 PNG。封面沿用项目头图的青绿色与花型构图，中英文使用 Google Sans 和思源黑体，文字已转为矢量轮廓。

各目录的 `preview-light.png` 和 `preview-dark.png` 展示当前插件界面；LED 另有一张横向字幕效果图。`cover-overview.jpg` 可一次查看全部封面。

界面由宿主的 Compose 控件与各插件当前编译类渲染，采用 API 35、393 × 852 dp、xxhdpi 的独立预览环境。LED 效果图来自插件自身的 HTML 渲染器。AI 对话与 LED 字幕使用示例内容，没有调用模型或读取真实会话。这些图片是界面预览，不代表真机功能测试结果。

`index.json` 记录每张图片的原始尺寸、SHA-256，以及插件源码提交与对应签名包的哈希。官方源通过 `assets` 注册素材，并在每个包的 `thumbnails` 中引用封面和界面图，支持横竖混排与点击放大。

## 重新生成

1. 准备 Google Sans、Source Han Sans CN 字体与 Material Symbols Rounded SVG；路径通过命令行传入。
2. 构建各插件，保留 `classes.jar`、依赖与签名包。
3. 运行 `tools/Build-PluginArtwork.py --fonts <字体目录> --symbols <图标目录> --plugin-root <插件目录> --catalog <官方清单>`。
4. 设置 `KIGTTS_ARTWORK_MANIFEST=build/plugin-artwork-runtime/manifest.json`，运行 Gradle：`-PpluginArtwork=true :app:testDebugUnitTest --tests com.kigtts.next.PluginArtworkCapture`。
5. LED 使用其 `assets/led/index.html`，调用 `KigttsSubtitle.update` 设置示例文字后截图为 `display.led/preview-led.png`。
6. 运行 `tools/Package-PluginArtwork.py --catalog <官方清单> --output <素材包.zip>`，再构建官方源。

封面文案与主题色分别维护在 `distribution/plugin-artwork.json` 和生成器中。本次素材完全通过 SVG、字体轮廓、原生 UI 和 HTML 渲染制作，未使用图像生成。

## 素材许可

- 封面构图及本项目原创素材：与主仓库相同的 MIT 许可。
- Google Sans、Source Han Sans：SIL Open Font License，附原许可与商标说明。
- Material Symbols Rounded：Google Material Design Icons，Apache-2.0；固定版本 `49d4db35df873165d6bd6ba09b063c7dafbac2f4`。
- 界面图片对应各插件实际许可证；具体源码提交见 `index.json`，各插件许可可在商店和源码仓库查看。
