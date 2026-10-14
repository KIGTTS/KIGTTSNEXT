# 内建拍立得相机

CameraX 1.6.2 的 `ImageCapture` 保存设备支持的高分辨率 JPEG，并采用质量优先模式与 JPEG 98。预览与拍照使用同一 `ViewPort`，取景只显示实际照片窗口，不显示白色相纸；完成后的编辑、预览和导出再使用相纸布局。拍摄结果来自相机而非屏幕截图。原始 JPEG 保留，不被滤镜覆盖；滤镜以原尺寸单张位图、32 行条带处理并生成另一份 JPEG，保留 EXIF 方向。照片上限为图库同样支持的 80 MP；内存不足或滤镜处理失败时交付原图并提示未应用滤镜，不缩为 2048 像素。

手机使用一个画幅图标切换矩形／方形，近方窗口和平板切换竖幅／横幅／方形。竖屏快门独立对齐底部中心，选图和镜头切换分别在两端，左上角保留返回；横屏按竖屏旋转后的手持关系重排，快门在右侧垂直居中，辅助操作在上、下两端。画幅、设置与滤镜入口由上侧移动到左侧，标签保持正向。滤镜默认收起，展开浮在照片下缘（横屏为右缘），不挤压照片窗口。

取景框周围留白足够时，56dp 操作条与 48dp 变焦条分别移到上、下外侧；横屏检查左右空间，把开关放左外侧、变焦放右外侧。判定包含 8dp 安全间距与 4dp 外缘，空间不足时仍在照片内。两组独立判断，画幅改变时位置连续过渡，滤镜保持覆盖照片窗。只移动控件，不缩小高画质 viewport、不重建 AndroidView、不因控件每帧位置变化重绑相机；上划返回仅标记真实照片窗，移动后的控件及曝光手柄继续优先响应拖动。

相机使用全屏布局，不显示标题、不保留 app bar 高度。透明操作层在真实安全区域左上放返回、右上放设置，加载圆环在返回触点内显示；横屏也直接覆盖内容。内容只避让状态栏、刘海和导航栏等实际系统安全边界。正式相机不重复显示照片内设置及底部取消；框外开关还要避让角落按钮，空间不足仍在取景框内下移。拒绝权限后仍可返回，倒计时可退出取消，实际保存保持返回保护。

统一相机设置支持三分线／方格／关闭、水平仪、关闭／自动／开启闪光灯和 0／3／10 秒倒计时，采用设置页的卡片、相连按钮组及动效弹窗。闪灯能力由实际镜头检查，前摄无 LED 时不覆盖用户已保存的选择；返回支持闪灯的镜头后恢复。倒计时只拍摄一次，重复快门可取消；返回或退入后台会取消，实际曝光及保存期间保持忙碌保护。辅助线和水平仪只叠在预览，不写入照片。重力数据按自然设备坐标和显示旋转映射；近乎平放时改成圆形水平指示，传感器不可用时不假装对齐。监听随页面和生命周期停用，绘制状态限流到 30 Hz，不驱动整个相机布局重新测量。

画幅变化时缓存一次小预览图作模糊过渡，矩形达到最终尺寸后只重绑一次，新画面出帧后淡出覆盖层。点按设置焦点，双指连续变焦；焦点旁的曝光手柄只在设备支持曝光补偿时出现。焦点失效、五秒未交互或离开页面时取消测光区域并复位曝光补偿，倒计时及拍摄期间不会因焦点计时结束改动曝光。

常规操作布局与功能参考 [Pixel Camera 设置与辅助线](https://support.google.com/pixelcamera/answer/2838995?hl=en-GB)、[Samsung 相机快捷控制](https://www.samsung.com/us/support/answer/ANS10010343/) 和 [构图指引](https://www.samsung.com/in/support/mobile-devices/composition-guide-feature-in-galaxy-devices/)。重力与坐标依据 [Android 传感器概述](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview) 和 [运动传感器](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)。这里实现应用所需的拍照控制，不复制相机素材或加入视频／专业拍摄模式。

默认使用标准 CameraX，HDR、自动优化与夜景在当前镜头实际支持时供用户选择；只尝试明确选中的模式，失败再回退标准模式，保留高分辨率／质量优先策略。原先按 Auto → HDR → Night 自动选择，在这台测试手机上落到 `ExtensionMode.NIGHT=3`；日志中切换出帧约 1.1–1.8 秒、一次拍摄约 2.75 秒。这是旧版本实测，新的路径仍需 Release 真机对照，不将模式切换视为已验证的性能提升。夜景适用于低光，多帧处理的耗时与设备实现有关，依据 [官方扩展模式](https://developer.android.com/reference/androidx/camera/extensions/ExtensionMode) 和 [CameraX 扩展](https://developer.android.com/media/camera/camerax/extensions-api)。只有设备支持并成功绑定才可启用 OEM 模式；照片质量、前摄镜像及权限撤回仍需手机验证。

底部变焦条使用实际 `ZoomState` 范围，可以拖动或点按有效的 1×／2×档位，横屏沿照片右缘纵向排列。不会虚构 0.6× 镜头。照片窗口可点按对焦与测光、双指变焦；设置内的手动对焦与快门由 Camera2 的实际硬件能力启用，仅标准模式支持。手动对焦写入 AF OFF 与镜头屈光度；手动快门写入 AE OFF、实际支持范围内的曝光时间和最近自动测光 ISO（尚无测光结果时使用硬件范围中值）。拍摄等待实际请求确认，不把滑条状态等同于已应用；切回自动清除覆盖项，离开页面解除控制，失败尝试恢复自动并提示。临时点按测光与手动模式分别控制 AF／AE，避免点按把手动设置覆盖。接口依据 [Camera2CameraControl](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2CameraControl) 和 [Camera2 手动曝光](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SENSOR_EXPOSURE_TIME)。长曝光、手动快门与闪灯的具体兼容性由设备决定，仍需硬件验证。

临时曝光与横屏变焦使用官方 MD3 `VerticalSlider`，由原生 Thumb、Track 与交互处理拖动。初触反馈不消费输入，曝光按真实补偿步进震动；自动恢复与手动快门下的禁用仍由相机会话控制。当前倍率及预设为至少 48dp 圆按钮，数字单行；当前倍率点按重置到有效 1×（范围不含 1× 时重置到真实最小值）。紧凑窗口只在能显示完整触点时保留预设，滑条仍可调全部真实范围。

原图／黑白／暖色／冷色／复古五个预设共用一套颜色矩阵；滤镜面板提供强度滑条，0 为原图、1 为完整预设，中间在线性矩阵空间插值。预览平缓过渡，保存应用相同目标矩阵，原始 JPEG 始终保留；0 强度不重编码。成片使用原生 Canvas 32 行条带替代 Kotlin 逐像素循环，保持原尺寸且不创建第二张完整位图。参考 [InstCam 的预览与保存共用矩阵设计](https://raw.githubusercontent.com/Genkaim/InstCam/main/app/src/main/java/com/genkaim/picocam/camera/FilterMatrix.kt)，这里使用自有预设与强度算法，没有复制代码或素材。

取景滤镜使用公开 `CameraEffect(PREVIEW)` 和 `SurfaceProcessor`，将相机 OES 帧直接通过 GLES 写入 PreviewView 输出。颜色动画只更新同一着色器参数，不重绑相机；原始 JPEG 和实况 MP4 不经过预览效果，因此保存与播放仍各处理一次。旧 `TextureView.setLayerPaint` 方案被手机实测证实无实时效果，已移除，不能把收到 Paint 当作滤镜生效。输入输出的裁剪、旋转、镜像和释放遵循 CameraX 的 Surface 契约，详见 [实时滤镜实现](../../../../../../../../../../docs/camera-live-filters.md)。没有每帧截图或 CPU 取景滤镜；画幅、镜头和刷新转场只暂存一次小图。

会话切换必须保留并解绑**同一 `SessionConfig` 实例**。此前使用 `ExtensionSessionConfig` 绑定后调用旧的 `unbind(preview, imageCapture)`，在 CameraX 1.6.2 会被忽略：本地 AAR 的 `LifecycleCameraProviderImpl.unbind(UseCase...)` 创建 `LegacySessionConfig`，`LifecycleCamera.unbind(SessionConfig)` 比较 `isLegacy()` 不一致时直接返回。因此首次可拍摄，切换相纸或镜头后旧设备继续占用、预览停帧。修复采用现代 session 的精确解绑，取消、启动失败、页面释放使用同一资源所有权路径，不用全局 `unbindAll()`。旧进程已经发生的泄漏须退出进程后重新验证。

启动成功须同时满足新会话 `OPEN` 与预览 `STREAMING`，先等待上一会话 `CLOSED` 与预览 `IDLE`，避免把旧帧当作新画面。启动等待有限；异步 OEM 失败直接跳到标准 CameraX，失败会显示可重试状态。明确诊断统一使用 `KigPolaroidCamera`。官方 [ProcessCameraProvider 会话绑定与精确解绑](https://developer.android.com/reference/androidx/camera/lifecycle/ProcessCameraProvider#unbind(androidx.camera.core.SessionConfig)) 支持这个资源合同；[Camera2Config](https://developer.android.com/reference/androidx/camera/camera2/Camera2Config) 的 1.6 默认实现已经是 CameraPipe，显式重复该配置并不能切回旧后端。

`PolaroidCameraCapture` 的原图和滤镜文件都是临时资源。宿主异步导入完两份后调用 `discard()` / `close()`；拍摄交付后叶子保持忙碌、拦截返回直到宿主导航离开，避免导入中的资源被重复拍摄或删除。未交付的取消与迟到回调会清理文件，页面离开及后台停用时只解绑本叶子的相机用例。

官方依据：[CameraX 发布说明](https://developer.android.com/jetpack/androidx/releases/camera)、[ViewPort 与分辨率](https://developer.android.com/media/camera/camerax/configuration)、[分辨率选择](https://developer.android.com/reference/androidx/camera/core/resolutionselector/ResolutionSelector)、[扩展会话](https://developer.android.com/reference/androidx/camera/extensions/ExtensionSessionConfig.Builder)。

## 布局与临时曝光

快门竖屏位于底部正中，横屏位于右侧正中；竖屏左侧选图对应横屏底部，右侧切换镜头对应横屏顶部。顶部开关组同样逆时针映射到左侧。相机返回、实况、设置归相机取景舞台，右上操作避开快门栏和变焦轨道；可用空间由画幅和安全区共同约束，留白不足时继续叠在取景区域。

对焦点旁的曝光补偿使用原生 MD3 VerticalSlider：黄色轨道和亮度图标、白色手柄、透明容器。两端预留原生无障碍范围，不靠裁剪隐藏越界；实际曝光步进触发反馈，失去对焦点后恢复自动曝光。页面转场期间临时禁止交互时保持相机按钮颜色，真实硬件能力不足或保存中的禁用状态仍保留。