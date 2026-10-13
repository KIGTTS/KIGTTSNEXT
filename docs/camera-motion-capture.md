# 原生相机实况采集

相机右上角的实况按钮开启后，CameraX 使用同一照片窗的 Preview、ImageCapture 和 VideoCapture。快门保留约 2 秒前录，以及实际 JPEG `onCaptureStarted` 时刻后的 1.2 秒短片；编码关键帧可能使前段略长。视频不是截图或拍照后才开始录制。环形预录每段最长 6 秒、24 MiB，平时只保留两段已完成历史，离开相机、换镜头或换画幅会停止并清理旧录制。

视频采用设备支持的 FHD，组合绑定不支持时尝试 HD。异步相机启动失败后直接回退到普通照片，不叠加多次完整黑屏等待。实况暂时使用标准照片处理，关闭后恢复此前选择的 HDR／夜景偏好；这是 CameraX 多用例组合的能力限制，不删除设备扩展功能。准备失败、视频编码失败、等待结束超时或保存附件失败都会保留已成功拍摄的照片。

已有麦克风权限时短片包含音轨；没有权限时静音录制，不为打开实况额外申请权限。透明预览默认静音，分享保留实际音轨。原始 JPEG 始终保留，滤镜生成独立显示图片。MP4 保留原始画面，附件记录同一滤镜的 20 项颜色矩阵，播放与导出统一应用一次；前置镜头的视频使用 CameraX 的前摄镜像模式。附件按显示图片 ID 保存于主程序自己的 `appdata/Kigtts/cards/motion`，没有新增 API9 卡片字段。

图库入口位于快门旁，左上角返回仍独立。Android 实况照片从原始 JPEG 的 XMP 和尾部 MP4 导入，先保留原图编码与 EXIF，不经过旧裁剪或降采样流程。有效展示时间用于定位快门，缺省或 `-1` 时间使用短片中点。无法读取短片时仍导入照片。所选资源只通过系统授权的本机 URI 读取；临时副本最多 128 MiB、静态图片最多 32 MiB／8000 万像素，短片最长 30 秒。原选取文件不会被修改或删除。

桌面回归覆盖预录跨段、曝光时刻、后录停止、环形资源上限、父取消、迟到编码回调、内部超时保照片、图库容器字节保持、附件归属与重复交付保护。编码器、OEM 多用例组合、录音、镜像及手机图库对导出实况的识别仍需真机验证。

依据官方 [CameraX 视频采集](https://developer.android.com/media/camera/camerax/video-capture)、[FileOutputOptions 时长限制](https://developer.android.com/reference/androidx/camera/video/FileOutputOptions.Builder)、[Finalize 输出状态](https://developer.android.com/reference/androidx/camera/video/VideoRecordEvent.Finalize) 和 [Android Motion Photo 格式](https://developer.android.com/media/platform/motion-photo-format)。
