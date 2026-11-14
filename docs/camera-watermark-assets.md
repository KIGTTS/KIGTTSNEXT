# 相机边框水印

水印资源通过 KPM 安装（`camera-watermarks`），XML 使用 UTF-8。样式只扩展照片外围，不改变照片画幅。

```xml
<watermark version="1" id="frosted" name="毛玻璃相框"
    layout="CLASSIC" position="BOTTOM" palette="GLASS" glassSource="FULL" edge="3" band="14"
    time="true" date="true" exposure="true" logo="true" location="false" weather="false"/>
```

- `palette="GLASS"` 适用于 `STANDARD`、`CHECK_IN` 和 `CLASSIC`。内置经典水印默认使用毛玻璃；已保存的旧纯色样式不变。
- `position="TOP"` / `"BOTTOM"` 决定水印条的显示位置；`glassSource` 独立指定取样区域，可填 `AUTO`、`TOP`、`BOTTOM`、`LEFT`、`RIGHT` 或 `FULL`。
- 省略 `glassSource` 或填 `AUTO` 时，每条外边框取相邻边缘。显式指定边缘或整张照片时，所有启用的边框共用一张模糊采样。设置 `edge="0"` 时只画水印条；照片内容本身不受影响。
- 毛玻璃按已裁切、调色后的照片取样，用重高斯模糊、轻微压暗和白色文字。实况输出使用同一张封面准备毛玻璃，不逐帧改变光影。使用显式采样字段的资源需要支持该字段的新版宿主。
- 宿主渲染可复用 `GaussianBackdrop.blurRegion`（照片局部模糊）或 `PhotoFrameGlass.prepare`（准备边缘条带并绘制到外围）；计算不应在界面线程运行。
