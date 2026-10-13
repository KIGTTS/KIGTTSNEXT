# 内建拍立得相机

CameraX 1.6.2 的 `ImageCapture` 保存设备支持的高分辨率 JPEG，并采用质量优先模式与 JPEG 98。预览与拍照使用同一 `ViewPort`，取景只显示实际照片窗口，不显示白色相纸；完成后的编辑、预览和导出再使用相纸布局。拍摄结果来自相机而非屏幕截图。原始 JPEG 保留，不被滤镜覆盖；滤镜以原尺寸单张位图、32 行条带处理并生成另一份 JPEG，保留 EXIF 方向。照片上限为图库同样支持的 80 MP；内存不足或滤镜处理失败时交付原图并提示未应用滤镜，不缩为 2048 像素。

手机使用一个画幅图标切换矩形／方形，近方窗口和平板切换竖幅／横幅／方形。横屏采用大取景区和右侧紧凑控制栏，滤镜位于取景下方。画幅变化时缓存一次小预览图作模糊过渡，矩形达到最终尺寸后只重绑一次，新画面出帧后淡出覆盖层。点按设置焦点，双指连续变焦；焦点旁的曝光手柄只在设备支持曝光补偿时出现。焦点失效、五秒未交互或离开页面时取消测光区域并复位曝光补偿，拍摄期间不会因计时结束改动曝光。

可用时优先 OEM Auto，再尝试 HDR / Night 扩展；会话或高分辨率组合不受支持时回退标准 CameraX。只有设备支持并成功绑定才能启用，因此不保证所有手机都具有系统相机相同的计算摄影效果。真实照片质量、OEM 扩展、前摄镜像与权限撤回需要手机验证。

会话切换必须保留并解绑**同一 `SessionConfig` 实例**。此前使用 `ExtensionSessionConfig` 绑定后调用旧的 `unbind(preview, imageCapture)`，在 CameraX 1.6.2 会被忽略：本地 AAR 的 `LifecycleCameraProviderImpl.unbind(UseCase...)` 创建 `LegacySessionConfig`，`LifecycleCamera.unbind(SessionConfig)` 比较 `isLegacy()` 不一致时直接返回。因此首次可拍摄，切换相纸或镜头后旧设备继续占用、预览停帧。修复采用现代 session 的精确解绑，取消、启动失败、页面释放使用同一资源所有权路径，不用全局 `unbindAll()`。旧进程已经发生的泄漏须退出进程后重新验证。

启动成功须同时满足新会话 `OPEN` 与预览 `STREAMING`，先等待上一会话 `CLOSED` 与预览 `IDLE`，避免把旧帧当作新画面。启动等待有限；异步 OEM 失败直接跳到标准 CameraX，失败会显示可重试状态。明确诊断统一使用 `KigPolaroidCamera`。官方 [ProcessCameraProvider 会话绑定与精确解绑](https://developer.android.com/reference/androidx/camera/lifecycle/ProcessCameraProvider#unbind(androidx.camera.core.SessionConfig)) 支持这个资源合同；[Camera2Config](https://developer.android.com/reference/androidx/camera/camera2/Camera2Config) 的 1.6 默认实现已经是 CameraPipe，显式重复该配置并不能切回旧后端。

`PolaroidCameraCapture` 的原图和滤镜文件都是临时资源。宿主异步导入完两份后调用 `discard()` / `close()`；拍摄交付后叶子保持忙碌、拦截返回直到宿主导航离开，避免导入中的资源被重复拍摄或删除。未交付的取消与迟到回调会清理文件，页面离开及后台停用时只解绑本叶子的相机用例。

官方依据：[CameraX 发布说明](https://developer.android.com/jetpack/androidx/releases/camera)、[ViewPort 与分辨率](https://developer.android.com/media/camera/camerax/configuration)、[分辨率选择](https://developer.android.com/reference/androidx/camera/core/resolutionselector/ResolutionSelector)、[扩展会话](https://developer.android.com/reference/androidx/camera/extensions/ExtensionSessionConfig.Builder)。
