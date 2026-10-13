# 相机实时滤镜

相机预览使用 CameraX `CameraEffect(PREVIEW)` 与 `SurfaceProcessor`，相机输出的 OES 纹理由 GLES2 颜色矩阵着色器直接绘制到 `PreviewView` 的输出 Surface。滤镜不再依赖 `TextureView.setLayerPaint` 的界面合成效果，不逐帧截图或在 CPU 上生成 Bitmap。

预览明确使用 SDR，着色器与保存照片共享 `PhotoFilters.coefficients` 的 20 项编码 RGB 系数。颜色偏移由字节值除以255转为采样值，强度和切换动画只更新同一个着色器的 uniform；不会因为每个动画帧重新绑定相机。`SurfaceOutput.updateTransformMatrix` 处理 CameraX 的照片窗裁剪、镜像和方向，取景框仍使用同一个原生 `PreviewView`，点按对焦和双指变焦保留。

滤镜状态第一次读取也必须观察动画时钟。旧实现初次返回静态矩阵时跳过了时钟读取，`snapshotFlow` 因此没有订阅后续动画，导致内部参数变化却未送达原生预览。现在静态和动画分支都读取同一时钟，切换预设或强度不会重建观察器；回归验证快速反向切换、中间矩阵和最终稳定值。

前后摄、重试和会话刷新在解绑前缓存一次小尺寸预览图，用模糊覆盖淡入过渡，等新会话显示就绪后淡出。取消或连续切换只清理本次拥有的动画，不回收仍在界面绘制的 Bitmap，也不在每帧取图；该覆盖限于取景区域，操作按钮继续显示。

effect 只声明 PREVIEW，不处理 IMAGE_CAPTURE 或 VIDEO_CAPTURE。原始 JPEG 仍保留，滤镜照片由原生照片处理器单独生成一次；实况视频仍保存原片和颜色矩阵元数据。画幅切换缓存从已处理的预览帧取图，不再次添加颜色矩阵，避免过渡画面双重滤镜。

解绑使用原本的现代 `SessionConfig` 实例，然后停止预览处理。输出先停止绘制、销毁其 EGL surface 再关闭；输入必须等 CameraX 的 `SurfaceRequest.provideSurface` 结果回调，所有输入都返还后才释放纹理/EGL和退出线程。失败或取消的启动也执行该顺序。GPU 初始化失败可回退普通相机，明确提示实时滤镜不可用，不宣称滤镜已生效。

上下文丢失时，切换到占位 EGL surface 失败也不能跳过原生 Surface／SurfaceTexture 释放，输出 EGL surface 仍尽力销毁。只有取得本处理器的 context 后才删除 GL 纹理名，避免误操作其它 context；首个异常保留，后续清理异常作为 suppressed 附加。桌面故障注入验证释放 API 的调用和异常保留，不代替驱动层资源回收验收。

日志标签 `KigPolaroidCamera` 中的 `GPU preview input`、`GPU preview first filtered frame` 和 `GPU preview released after all camera input leases returned` 分别标记输入提供、真实帧绘制和最终释放。`GPU preview processing failed` 保留原始异常。

桌面回归验证所有预设/强度的共享系数、PREVIEW-only target、真实 CameraX 请求的输入使用计数、替换输入、迟到请求和失败释放。注入渲染器的协议测试不能证明手机 GPU 的实际像素效果；新 Release 的黑白效果、强度、画幅切换、前摄镜像和返回释放仍需真机验收，不安装测试 APK。

依据官方 [CameraEffect](https://developer.android.com/reference/androidx/camera/core/CameraEffect)、[SurfaceProcessor](https://developer.android.com/reference/androidx/camera/core/SurfaceProcessor)、[SurfaceOutput](https://developer.android.com/reference/androidx/camera/core/SurfaceOutput) 与 [SessionConfig](https://developer.android.com/reference/androidx/camera/core/SessionConfig) 的公开接口。官方 [CameraX Media3 适配器](https://developer.android.com/jetpack/androidx/releases/camera-media3) 当前仅 alpha04，本实现没有新增该依赖；直接采用已使用的 CameraX 1.6.2 公共 GPU 接口。
