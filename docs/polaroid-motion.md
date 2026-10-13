# 拍立得实况照片

Android 实况采用 CameraX 视频预录和同一拍摄请求中的 JPEG。已有麦克风权限时录入音轨，其余情况静音，不额外请求权限。滤镜后的显示图片 ID 是附件的键；原始 MP4、快门时刻、方向及颜色矩阵保存在 `appdata/Kigtts/cards/motion`。这是主程序的本地附加信息，没有修改 API9 的 `PolaroidCard` ABI。未保存的编辑重拍不会覆盖原照片的附件，复制照片则共享同一图片附件。采集和图库导入的行为见 [相机实况采集](camera-motion-capture.md)。

只有透明预览窗口会创建播放器，播放控件位于相纸下方。宫格、相册封面和逐张浏览始终使用静态缩略图。预览只缩放整张纸，不修改裁剪或贴纸；收到的只读照片隐藏编辑入口，仍可播放、分享和添加为快捷文本媒体卡片。

`exportPolaroidMedia(stickers, card, includeFrame, includeStickers)` 为分享和快捷文本导出统一入口。静态照片返回 PNG；实况通过 Media3 Transformer 合成带纸框、文字、贴纸、Logo 和当前照片构图的视频，再封装为 `*MP.jpg`。关闭相框选项会裁出当前照片窗；关闭贴纸选项只移除附加图层。视频直接复用静态 Renderer 的透明照片窗 overlay，与预览使用同一 GPU effect。只保留两张低分辨率模糊缓冲与一张静态 overlay，不把全部视频帧载入内存。

容器按 [Android Motion Photo 格式](https://developer.android.com/media/platform/motion-photo-format) 写入 JPEG 主图与尾部 MP4、`MotionPhotoVersion=1`、快门时间和 Container 项长度，提交前由现有 parser 校验。着色器遵循 [Media3 GlEffect 的线性 BT.709 工作空间](https://developer.android.com/reference/androidx/media3/effect/GlEffect)，先转换 Bitmap 编码颜色再合成；拍照时的颜色矩阵在编码 RGB 域处理后转回线性。

桌面定向测试覆盖真实 JPEG/MP4 容器往返、静态与透明 overlay 像素、相框/贴纸选项、附件重启及未保存重拍隔离、预览控件位置和播放状态。硬件 CameraX 预录、GL 逐帧合成和手机相册对输出文件的识别仍需真机回归；桌面播放器夹具不代替硬件编码验收。
