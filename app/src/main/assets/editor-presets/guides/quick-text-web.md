# 快捷文本网页示范

源码位于 [examples/quick-text-web](../examples/quick-text-web)。两个插件的「快捷文本网页」页可以离线预览，也可以在确认后添加到快捷文本。放入条目后，该网页使用宿主的 `PAGE` 内容类型，可从快捷文本编辑器继续管理文件。

| 示例 | 源码 | 要点 |
| --- | --- | --- |
| 界面 | `index.html`、`gallery.css`、`gallery.js` | 表单、按钮组、开关、输入、语义配色、焦点和网页触感 |
| Canvas 2D | `canvas.js` | 通过 Pointer Events 记录归一化笔迹。缩放时更新像素画布，笔迹保持，可清空 |
| WebGL 3D | `webgl.js` | 使用 WebGL 1 绘制一个 36 顶点立方体。拖动或滑条调整视角，按需绘制，处理上下文丢失和硬件不支持 |

## 网页 UI 规范

- 引用宿主的 `/kigtts/theme.css`、`/kigtts/m3e.css` 和可选的 `/kigtts/components.js`。使用 `--md-sys-color-*` 及匹配的 `on-*` 前景色，遵循 `--kigtts-corner` 与 `--kigtts-safe-*`。普通浏览器预览用 CSS 备用值，KIGTTS 使用当前实际主题色。
- 对 DOM 样式使用 CSS 变量。Canvas 和 WebGL 在 `kigtts-theme-change` 时重新取色并绘制。同一亮暗模式下的主题色改变也会触发该事件。
- 页面使用流式网格、可换行文案和最大宽度，给控件至少 48 CSS px 手指点击区。图标按钮提供可访问名称，保留键盘焦点，尊重减少动态效果，并测试竖屏、横屏、多窗口及 IME。
- 画布在可见时按 CSS 尺寸和设备像素比绘制。绘图时限制像素倍率与笔迹数量，只有画布区域接管手势。三维模型可用滑条操作，WebGL 不可用时显示文本状态。
- 网页无 CDN、联网权限和原生动作白名单。业务网页需要调用本插件时，先向宿主提交允许的动作 ID，再使用 `Kigtts.postMessage`。涉及相机、麦克风、体感和下载时，遵循宿主授权并在退出时释放资源。
- 「触感反馈」演示从真实点击中调用 `KigttsUI.haptic('selection')` 和 `KigttsUI.haptic('confirm')`。请求遵循宿主设置和系统策略。

开发时保持网页主文件为 `index.html`，CSS、脚本和资源使用相对路径。执行 `./scripts/Sync-CatalogWeb.ps1` 同步两套插件资源，执行 `./scripts/Build-CatalogKit.ps1` 打包完整网页和开发套件。独立网页 ZIP 包含关联文件，插件中的「导出 HTML 主文件」用于演示单文件导出接口。

宿主协议详见 [KIGTTS Web UI 合同](https://github.com/KIGTTS/KIGTTSNEXT/blob/main/docs/web-ui.md)。
