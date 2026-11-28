# KIGTTS仓库结构

SDK 与外置插件独立发布。Android 宿主、运行时与内置功能在 [KIGTTS/KIGTTSNEXT](https://github.com/KIGTTS/KIGTTSNEXT) 维护。主程序默认构建只需初始化 `.sdk`。

|组件|仓库|许可|
|---|---|---|
|通用SDK|[plugin-sdk](https://github.com/KIGTTS/plugin-sdk)|MIT|
|tts-support|[tts-support](https://github.com/KIGTTS/tts-support)|MIT|
|cloud-agent|[plugin-cloud-agent](https://github.com/KIGTTS/plugin-cloud-agent)|MIT|
|lua-runtime|[plugin-lua-runtime](https://github.com/KIGTTS/plugin-lua-runtime)|MIT|
|native-builder|[plugin-native-builder](https://github.com/KIGTTS/plugin-native-builder)|MIT|
|piper-tts|[plugin-piper-tts](https://github.com/KIGTTS/plugin-piper-tts)|GPL-3.0-only|
|sensevoice-asr|[plugin-sensevoice-asr](https://github.com/KIGTTS/plugin-sensevoice-asr)|GPL-3.0-only|
|streaming-asr|[plugin-streaming-asr](https://github.com/KIGTTS/plugin-streaming-asr)|MIT|
|ui-debug|[plugin-ui-debug](https://github.com/KIGTTS/plugin-ui-debug)|MIT|
|quick-text-tools|[plugin-quick-text-tools](https://github.com/KIGTTS/plugin-quick-text-tools)|MIT|
|kigusay-backup|[plugin-kigusay-backup](https://github.com/KIGTTS/plugin-kigusay-backup)|GPL-3.0-only|
|led-caption|[plugin-led-caption](https://github.com/KIGTTS/plugin-led-caption)|MIT|

各仓库有独立的 Gradle 入口、固定的 SDK 子模块、源码、测试和许可文件。Piper 和 SenseVoice 保留 GPL-3.0-only。首次拆分的来源提交记录在各仓库的 `SOURCE_ORIGIN.md`，旧发布包保留对应的历史源码。

## 已并入主程序

|功能|当前源码|历史仓库|
|---|---|---|
|拓展市场|[`market/`](../market)|[plugin-package-store](https://github.com/KIGTTS/plugin-package-store)|
|朗读服务|[`reading/`](../reading)|[plugin-speech-service](https://github.com/KIGTTS/plugin-speech-service)|
|识别服务|[`speech-recognition/`](../speech-recognition)|[plugin-recognition-service](https://github.com/KIGTTS/plugin-recognition-service)|
|快捷手势|[`SubtitleGestures.kt`](../app/src/main/java/com/kigtts/next/quickactions/SubtitleGestures.kt)|[plugin-quick-text-gestures](https://github.com/KIGTTS/plugin-quick-text-gestures)|

朗读服务保留 GPL-3.0-only。内置朗读和识别继续为独立语音引擎提供原有的服务标识。

## 构建与测试

```powershell
git submodule update --init .sdk
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

普通单元/UI测试使用仅位于test源集的界面探针，不链接真实调试插件。跨插件的3套集成测试位于`app/src/pluginIntegrationTest`，仅显式启用时加载独立仓库：

```powershell
git clone https://github.com/KIGTTS/plugin-cloud-agent ../KIGTTS-Plugins/cloud-agent
git clone https://github.com/KIGTTS/plugin-lua-runtime ../KIGTTS-Plugins/lua-runtime
git clone https://github.com/KIGTTS/plugin-quick-text-tools ../KIGTTS-Plugins/quick-text-tools
./gradlew.bat -PpluginIntegration=true -PpluginSourceRoot=../KIGTTS-Plugins :app:testDebugUnitTest --tests "com.kigtts.next.AgentCapabilitiesIntegrationTest" --tests "com.kigtts.next.AgentImageEditingRuntimeTest" --tests "com.kigtts.next.QuickTextToolsExampleTest"
```

各插件仓库单独构建前执行`git submodule update --init --recursive`。发布使用仓库Package.ps1与明确的发布者私钥；私钥、模型、供应商令牌、用户素材均不得入库。主仓库tools/Build-EnginePlugins.ps1只委派独立仓库打包并汇总产物；同版本不可覆盖。

2026-10-22 主仓库已从LHT02转移至KIGTTS组织，旧地址由GitHub重定向。本地已有克隆应执行`git remote set-url origin https://github.com/KIGTTS/KIGTTSNEXT.git`；SDK子模块继续使用组织下的`plugin-sdk`。KPM网页和私有模板库仍保留当前地址与可见性，单独安排迁移。
