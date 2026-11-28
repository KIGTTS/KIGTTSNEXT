# KIGTTS

[English](docs/README.en.md) · [日本語](docs/README.ja.md) · [한국어](docs/README.ko.md)

![KIGTTS](ARTS/Promotion/github-cover.webp)

套上头壳，想聊也能聊。KIGTTS 是给 Kigurumi 玩家和同好用的随身对白框。想集邮、扩列、问路，举起手机就能把想说的话亮出来。

## 出门带上它

- **打字给人看。** 字幕可以放得很大。常用的话提前存好，一点就上屏，图片、声音和网页也能一起放进快捷文本
- **听不清就看字幕。** 可以朗读你打的字，也能把听到的话变成字幕。用别的应用时，浮窗也能留在手边
- **拍一张再走。** 随手拍、用拍立得相框出片，或者打开画板边写边聊
- **碰到同好就递张名片。** 把二维码、社媒和图片排成自己的名片，收到的也能收进扩列收藏夹
- **按自己习惯来。** 换个主题色，装喜欢的语音或贴纸，也可以给自己做个小插件

## 从旧版过来

1.0.0 正在准备。正式版沿用旧版 Android 包名和签名。升级欢迎页会跳过权限和预设快捷文本；也可以在「备份与还原」里打开旧版备份。Piper 语音包可以带过来，缺少引擎时会提示下载。

## 想做点自己的东西

在实验性功能里打开插件编辑器，可以从 Lua 或原生的「插件模板」开始。模板包含字幕、语音、网页、触感和设置控件的完整示例。

- [插件模板](https://github.com/LHT02/KIGTTS-PLUGIN-TEMPLATES)
- [插件 SDK 与接口](docs/plugin-api.md)
- [插件界面规范](app/src/main/assets/editor-presets/guides/plugin-ui.md)
- [插件、资源包与安装](docs/plugin-packages.md)
- [仓库分工与许可证](docs/repositories.md)

### 本地构建

需要 JDK 17、Android SDK 36 与 PowerShell 7。在 `local.properties` 指定 `sdk.dir`。

```powershell
git submodule update --init .sdk
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:assembleRelease
./gradlew.bat :plugin-api:test :plugin-runtime:testDebugUnitTest :app:testDebugUnitTest
```

APK 位于 `app/build/outputs/apk/`。用 `-PkigttsPreviewPackage=true` 构建可并存的预览包。正式覆盖安装的签名构建及版本冻结说明见 [1.0.0 发布准备](docs/release-1.0.0.md)。

主程序和 SDK 采用 MIT 开源许可，各插件和美术资源遵循各自附带的授权说明。 [美术物料](ARTS/README.md)
