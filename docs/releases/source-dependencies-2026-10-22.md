# KPM 服务依赖与商店安装引导

主程序实现提交：`fd523a33e2d9cb41bab60fb2488e671e70fdcbd3`。插件商店继续使用已发布的 0.10.1/API15，无需重装商店即可读取新目录依赖。

## 行为

- Piper 的前置为语音合成服务 `^0.8.0`；SenseVoice、流式识别的前置为语音识别服务 `^0.8.0`。商店按原有版本解析机制准备缺少的服务，确认后先安装前置，再安装引擎。
- 没有商店时打开添加源链接，先提示下载官方商店；下载前确认，验签后再次确认安装。已安装但停用时引导启用。源地址跨重启保留，商店准备好后继续源确认。
- 启动加载完成前不误报商店缺失；取消和离线失败不自动重试、不丢失源地址；不提前连接待添加的第三方源。
- 手动导入独立原生包暂不自动查源补依赖，也不自动启用用户停用的依赖服务。

## 验证与交付

- 宿主重点 12 项、商店 52 项回归通过；Release、R8、Lint 通过（0 error，95 warning，3 hint）。
- 新目录通过 schema，保留全部 20 个历史版本的二进制摘要、大小、发布者、镜像与兼容性属性。
- [官方源](https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Official-Plugins/resolve/master/index.json?view=false)及 Android 元数据已完成匿名 API 和常用下载地址的字节核对；平台短时缓存旧元数据已排除。
- [主程序 APK](https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Official-Plugins/resolve/master/android/KIGTTSNEXT-api15-20261022-b3cebe62f38c.apk?view=false)：7,856,362 字节，SHA-256 `b3cebe62f38cb53d19e96c81d3bba09b6e75e2cc83d273414f725caea518fd25`。优化且不可调试的预览 Release，使用原本本地开发测试证书签名，非生产签名。
- 保留手机当前手动测试状态，本轮未覆盖安装，未声称真机交互验收完成。
- KPM 静态离线包 `KPM-static-api15-b3cebe62f38c.zip`：SHA-256 `1a191fb2a95191565c68a0d691bdd922de4aa1a6b66d18936b038421047e835c`；网页 7 项回归通过。
