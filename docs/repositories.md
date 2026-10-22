# KIGTTS仓库结构

SDK与外置插件分别独立发布；本仓库负责Android宿主与运行时。主程序默认构建只需初始化`.sdk`，不需要获取引擎、助手或商店源码。

|组件|仓库|许可|
|---|---|---|
|通用SDK|[plugin-sdk](https://github.com/KIGTTS/plugin-sdk)|MIT|
|tts-support|[tts-support](https://github.com/KIGTTS/tts-support)|MIT|
|cloud-agent|[plugin-cloud-agent](https://github.com/KIGTTS/plugin-cloud-agent)|MIT|
|lua-runtime|[plugin-lua-runtime](https://github.com/KIGTTS/plugin-lua-runtime)|MIT|
|native-builder|[plugin-native-builder](https://github.com/KIGTTS/plugin-native-builder)|MIT|
|package-store|[plugin-package-store](https://github.com/KIGTTS/plugin-package-store)|MIT|
|piper-tts|[plugin-piper-tts](https://github.com/KIGTTS/plugin-piper-tts)|GPL-3.0-only|
|sensevoice-asr|[plugin-sensevoice-asr](https://github.com/KIGTTS/plugin-sensevoice-asr)|GPL-3.0-only|
|streaming-asr|[plugin-streaming-asr](https://github.com/KIGTTS/plugin-streaming-asr)|MIT|
|speech-service|[plugin-speech-service](https://github.com/KIGTTS/plugin-speech-service)|GPL-3.0-only|
|recognition-service|[plugin-recognition-service](https://github.com/KIGTTS/plugin-recognition-service)|MIT|
|ui-debug|[plugin-ui-debug](https://github.com/KIGTTS/plugin-ui-debug)|MIT|
|quick-text-tools|[plugin-quick-text-tools](https://github.com/KIGTTS/plugin-quick-text-tools)|MIT|

各仓库有独立Gradle入口、固定SDK子模块、源码、测试和LICENSE/NOTICE；Piper、SenseVoice及合成服务保留GPL-3.0-only，MIT SDK不改变它们的许可。首次拆分的来源提交记录在各仓库SOURCE_ORIGIN.md；旧发布包保留原对应源码，不以新源码冒充历史二进制来源。

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

KPM网页、私有模板库和Android主仓库的组织迁移独立安排，当前地址/可见性仍保留；不把组织下已发布的插件仓库等同于它们已迁移。
