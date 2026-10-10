# 媒体卡片封面与网页安全区

快捷条目和媒体库使用同一封面组件。编辑、拖动浮层和条目容器变换复用缓存像素；文字位于左侧，画面重点位于右侧。左侧采用半透主题色遮罩（亮色最高58%、深色最高66%），以平滑曲线降低到右侧；高斯核的强度连续变化，逐渐恢复清晰画面。模糊仅在后台生成封面时计算，滚动与按压不执行实时模糊。

## 自动与手动取景

Kotlin 实现移植自 [smartcrop.js 固定修订](https://github.com/jwagner/smartcrop.js/blob/0e207ed910625f91b07d35865c71a9621a63ebdb/smartcrop.js)，保留边缘细节、饱和度、肤色通道、可选重点区域、候选遍历和评分。原版默认参数通过独立 JS 参考数据核对；原生封面策略关闭肤色权重，避免偏向 Kigurumi 面具或表情包的颜色。当前不使用人脸检测、OCR 或训练模型，也不能理解面具头部和文字含义。重点矩形可供后续可选检测器增强。

透明素材忽略完全透明像素的 RGB，先裁去无内容边缘并加6%留白，完整显示剩余内容。普通素材按细节和颜色选取；如果散布的细节被裁切丢失较多，则保守地完整显示。网页默认保全预览。完整显示时用相同内容的模糊背景补齐比例；它是启发式规则，不能保证识别所有带字表情包。

媒体库条目“管理 → 缩略图取景”提供相连按钮：

- **智能取景**：自动选择并按内容规则保全。
- **完整显示**：适合带字或内容分散的素材。
- **手动取景**：点击源封面设置重点，查看卡片预览后保存。

焦点以源图归一化坐标保存，旧媒体记录默认智能取景。该选择与大字幕的“适应/填充”独立，调整封面不会改变实际播放或图片显示。

## 来源与缓存

图片与实况照片使用静态封面，并处理 EXIF/HEIF 方向；视频只提取一个缩小的封面帧，不启动列表播放器。Android27+直接提取缩小帧，Android26兼容路径会短暂解码完整帧后缩小。视频编码兼容性仍由设备决定。网页/HTML仅截取已经显示过的 WebView；未访问、进程重建或页面已编辑时用类型图标，不为列表创建隐藏网页或额外发起网络请求。

IO负责解码，后台计算线程负责裁切和模糊，取消的卡片任务可中止；并发消费者去重。源图上限384像素，已显示网页快照上限512像素，卡片结果上限384×256。尺寸分桶避免按压/布局每改变一个像素就重算。仓库级内存LRU预算为4MiB源图、6MiB封面、4MiB网页快照；临时计算内存与系统解码器另计。交接时立即复用上次封面，新的尺寸结果准备好后替换。

源缓存由资源路径、文件时间戳/长度、网页版本识别，渲染缓存另包含模式、焦点和目标尺寸。返回前台会异步检查资源。删除引用移除缓存；不回收仍可能正在显示的 Bitmap。封面不写外置文件；插件原始与网络资源继续留在所属插件 Appdata，模型和引擎边界不变。

MIT许可和两份原始版权年份保留于 [third-party/smartcrop](../third-party/smartcrop/NOTICE.md)，完整许可随 APK 在 `assets/licenses/smartcrop-MIT.txt` 分发。应用运行时不包含 smartcrop JavaScript 或 Node。

## 网页覆盖与内容安全区

WebView与图片/视频一样铺满整张大字幕卡片，四角按钮在其上方。宿主按实际Compose布局测量上下操作区及横向留白，把物理尺寸转换为CSS viewport单位。页面原有body边距折入内边距，body背景延伸到卡片边缘；顶部/底部自动预留内容空间，重复更新不累加，横屏、全屏、窗口和字号布局变化时同步。相同参数不反复执行DOM修改。

插件网页可读取四个CSS变量：`--kigtts-safe-area-top`、`--kigtts-safe-area-right`、`--kigtts-safe-area-bottom`、`--kigtts-safe-area-left`；也可使用 `window.KigttsSafeArea` 以及 `kigtts-safe-area-change` 事件的 `detail`，数值单位均为CSS像素。

```css
/* body普通流已自动留白；固定定位控件需使用这些变量。 */
.floating-action {
  position: fixed;
  right: calc(var(--kigtts-safe-area-right, 0px) + 12px);
  bottom: calc(var(--kigtts-safe-area-bottom, 0px) + 12px);
}
```

```javascript
window.addEventListener('kigtts-safe-area-change', event => {
  const {top, right, bottom, left} = event.detail;
  // 例如更新canvas或自定义布局中的内容边界。
});
```

第三方网页自己的固定定位/绝对定位及复杂自定义布局无法由body留白保证兼容，需要页面适配上述变量。原网络来源和插件动作权限仍适用。

## 验证

固定原版JS提供10组矩形与全部评分通道参考，另覆盖小图/长图/透明图/取消、透明表情留白、分散海报保全与手动焦点边界。Native Graphics验证连续模糊、并发缓存复用、文件更新失效、网页快照版本、无缩略图文件写入及旧记录迁移；Compose覆盖亮暗封面、编辑与手动取景保存、单个网页的整卡片边界和安全区参数。独立Node执行实际注入脚本，验证幂等与viewport换算。Robolectric无Chromium排版，不能代替真实网页兼容性或GPU性能验收。

本地原生渲染预览（合成测试素材）：[亮色](images/media-thumbnail-light.png)、[深色](images/media-thumbnail-dark.png)、[编辑](images/media-thumbnail-edit.png)、[取景](images/media-thumbnail-framing.png)。

2026-10-10 主程序 222 项完整回归、Debug/Release/R8 及两种 Lint 通过。同证书非 debuggable Release 3.439 MiB 已安装，手机 APK 摘要一致。自建测试页在真实 Chromium 中验证整卡片视口高度与安全区，并显示视频实际解码和动态照片自动播放；本次临时素材已清理。完整网页兼容性、全部窗口变化及长期 GPU 性能仍需专项验收。
