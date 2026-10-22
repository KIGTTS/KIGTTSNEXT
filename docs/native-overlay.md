# 悬浮快捷入口

主软件原生功能，入口在“设置 → 外观与交互 → 悬浮窗”。首次默认关闭，由用户通过系统悬浮权限开启。闲置贴边收起与淡化默认开启；拖动调整位置，点按展开，长按打开设置。展开使用主软件 M3E 色板、圆形至 Cookie12Sided 形变和 Expressive 动效。

展开列表可以上下拖动转动，支持最多24个入口、手柄排序、显示文字开关和每个入口独立的小窗偏好。内置大字幕、拍照、拍立得、画板、快捷文本；可选安装的可启动应用及插件注册的候选入口。仅查询 MAIN/LAUNCHER，不申请 QUERY_ALL_PACKAGES。

窗口按系统栏、刘海和当前屏幕重新定位；收起时只保留80dp小窗口，展开时也不创建全屏触摸遮罩。与原有悬浮聆听字幕共存。服务使用 specialUse 前台类型，有持续通知和关闭操作；不在开机时自行启动、不启动录音。系统收回悬浮权限后关闭服务。原生系统授权需真机手动完成。

小窗通过公开 ActivityOptions.setLaunchBounds 和 FEATURE_FREEFORM_WINDOW_MANAGEMENT 提示系统；系统有最终决定权。不开放的设备正常打开并提示，不调用隐藏的厂商窗口接口。[Android ActivityOptions](https://developer.android.com/reference/android/app/ActivityOptions)

## 插件接口（API22）

包声明 `overlay` 及 permissionPurposes。通过调用方绑定的 AppBroker 使用 `AppCommand.Overlay`：

- `status`：查询开关与系统授权，不能开启悬浮窗。
- `register`：注册 OverlayEntrance 候选，不自动添加。
- `request-add`：注册并排队原生确认弹窗，用户手动同意后才添加。完全访问不绕过本权限和添加确认。
- `bubble`：显示原生文字气泡，可带图标文字按钮，actionId/pageId二选一。文本最多180字，按钮最多24字，2–30秒自动关闭。
- `clear-bubble`：只关闭自己的气泡。

入口绑定插件ID和签名发布者。停用/卸载清除候选、待确认和气泡；保存的入口在重新打开时核验启用状态、发布者和页面。导出 Activity 只接受宿主创建的一次性不透明入口令牌，不接受任意插件ID或操作参数。

## 本轮验证范围

电脑 Robolectric/Compose 覆盖窗口生命周期、权限拒绝、旋转安全范围、入口排序、拒绝/确认、伪造与重复路由、展开/收起、亮暗主题及全局震动开关。用户已带走设备，本轮不宣称通过厂商悬浮授权、真实跨应用拖动、小窗行为或实机动效验收。
