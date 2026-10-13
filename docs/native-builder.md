# 原生构建扩展

`tools.native-builder` 是独立的 API9/MIT `.kigplugin`，从插件编辑器的“Java 与 Kotlin”入口进入，也可从设置的“开发工具”进入。
主程序不携带任何编译器。扩展提供源码项目、文件管理、二级源码编辑、构建、签名、打包与分享。
Lua 工具直接由插件编辑器运行；完整 `KigPlugin` 的 Java/Kotlin 源码由这个可选扩展构建成原生 `.kigplugin`，继续经过宿主的发行者/权限确认。

## 源码项目

支持项目目录或 `.kigsource` ZIP。根目录为：

```
plugin-spec.json
sources/com/example/MyPlugin.kt  # .java 同样支持，多文件/混合语言
dependencies/*.jar             # 可选，编译类路径且打入 DEX
assets/...                     # 可选，不需要主程序资源表
lib/<abi>/*.so                 # 可选，已经编译的原生库
LICENSE
NOTICE.md
```

```json
{
  "id": "example.my-plugin",
  "name": "我的插件",
  "version": "0.1.0",
  "versionCode": 1,
  "entryClass": "com.example.MyPlugin",
  "apiVersion": 9,
  "minSdk": 26,
  "runtime": "android-dex",
  "license": "MIT",
  "capabilities": ["ui", "resources"]
}
```

入口必须实现公开的 `KigPlugin`；descriptor 的 ID、版本、API、许可证须与 spec 相同。
项目最多 512 文件/64 MiB；Java/Kotlin 源码最多 64 文件/1 MiB。
目录之外的路径、坏 ZIP、重复文件名与不支持的根文件不会被导入。LICENSE 和 NOTICE.md 必须存在。
SDK 含 Kotlin/JVM 默认方法，Java 实现可以只重写 `getDescriptor()` 和 suspend 的 JVM 签名 `execute(..., Continuation)`，或正常提供页面/按钮等能力。

编辑器可新建 Java 或 Kotlin 模板；所有项目拥有自己的文件夹。
文件管理支持目录、面包屑、多选、复制、移动、重命名、删除确认、批量导入 JAR 或媒体。
源码、HTML/CSS/JSON 等文本资源和项目 spec 使用独立的二级 `SourceEditor`，复用宿主的行号/键盘快捷栏。
错误的 spec 保存会回滚，避免项目因为短暂的无效 JSON 从列表消失。
构建时复制一个源码快照，用户继续编辑不会改变这次输出。

## Android 构建工具链

第一次使用需要导入独立的 Android 兼容工具链。它是 ZIP/目录，不是主程序或 APK 升级。
根 `toolchain.json` 例如：

```json
{
  "name": "Android Java toolchain",
  "languages": ["java"],
  "dexFiles": ["code/classes.dex"],
  "classpath": ["classpath/android.jar", "classpath/plugin-api.jar", "classpath/kotlin-stdlib.jar"],
  "javaClass": "org.eclipse.jdt.core.compiler.batch.BatchCompiler",
  "kotlinClass": "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
  "d8Class": "com.android.tools.r8.D8",
  "files": {
    "code/classes.dex": "sha256",
    "classpath/android.jar": "sha256",
    "classpath/plugin-api.jar": "sha256",
    "classpath/kotlin-stdlib.jar": "sha256",
    "licenses/LICENSE": "sha256"
  }
}
```

`files` 的每项必须为真实 64 位小写 SHA256；清单必须覆盖除根 manifest 外的全部文件，并且包含许可证。
工具链最大 256 文件、512 MiB。导入只校验/暂存，用户确认后才启用。
SHA 清单验证的是文件完整性，不能代替来源信任；这些编译器会以原生插件权限运行。

Android 使用 `DexClassLoader` 载入 `dexFiles`，不能把普通 JVM JAR 当成 Android 可运行编译器。
Java 的入口是官方 `BatchCompiler.compile(String[], PrintWriter, PrintWriter, CompilationProgress)`；使用 Android `android.jar` 的 bootclasspath，禁用注解处理。
Kotlin 的入口是兼容 `K2JVMCompiler.exec(PrintStream, String[])`，以 `-no-jdk/-no-stdlib/-no-reflect` 加显式 Android/SDK 类路径编译。
Kotlin 与 Java 混合项目先编译 Kotlin，再编译 Java。
D8 使用官方 `D8Command.parse(...)` 和 `D8.run(...)` 将项目 class 与运行依赖 JAR 转成 Android DEX。

**普通桌面 Kotlin 编译器并不自动兼容 Android/ART。**
工具链的 Kotlin 入口可由经过 Android 适配的扩展提供同样签名；未声明 Kotlin、缺少入口或实际运行失败会明确报错，不产生伪造成功结果。
“已配置”只表示已确认工具链资源，不等于手机兼容性已验证。
仓库提供 `engine-plugins/native-builder/tools/Build-Toolchain.ps1`，从本地受控依赖生成 Java `.kigtools`。
脚本已经实际完成 ECJ/D8 的 DEX 转换；默认只声明 Java。`-ExperimentalKotlin` 明确开启未适配桌面 Kotlin 编译器的实验包生成。
生成脚本保留依赖中已有的许可证、生成逐项 SHA 并记录来源，不改变主程序包体。
本轮桌面测试使用真实 ECJ 3.39.0、Kotlin 2.2.21 和 SDK36 D8，成功生成实际 `KigPlugin` DEX；手机工具链适配和安装仍需在设备连接后验证。

## 签名、打包与分享

DEX 进入 `code/classes*.dex`，项目 assets/native libraries 与 LICENSE/NOTICE 进入独立包。
打包器计算每项 payload SHA256，生成宿主支持的 `plugin.json`；本扩展生成并保存自己的 RSA3072 私钥，使用 SHA256withRSA 签署原始 manifest。
包包含 `signature/public.der` 和 `signature/manifest.sig`，不包含私钥、源码、构建缓存或编译器。
相同手机上后续版本沿用同一签名身份；保留本扩展数据以避免更新时丢失发行者身份。

全部资源在：

```
appdata/Kigtts/plugin/plugindata/tools.native-builder/
  projects/<id>/...
  toolchains/<uuid>/...
  staging/<uuid>/...
  inbox/<uuid>/...
  imports/...
  tmp/build-<uuid>/...
  config/publisher-private.der
  config/publisher-public.der
  exports/<id>-<version>.kigplugin
  exports/<id>.kigsource
```

“保存或分享插件”调用宿主 `ExportResource`，由系统保存/分享界面处理。
本扩展不会绕过插件管理器直接安装编译结果，也不会把“已生成包”显示成“已安装插件”。
取消在编译阶段边界阻止后续打包；第三方编译器的一次阻塞调用不保证立即退出。关闭扩展会取消任务，宿主可回收独立工作进程。

## Agent 与编辑器连接

扩展只导出 `import`、`build`、`open` 自动化动作。工具链确认、源码导入确认、删除等动作保留给用户。
Agent 用 `AppCommand.DeliverResource("tools.native-builder", source, "import")` 交付 `.kigsource` 或源目录，打开检查页面；这一步只创建待检查草稿。
用户确认保存后，可调用 `AppCommand.PluginAction("tools.native-builder", "build", {"projectId":"..."})` 发起构建。
`AppBroker` 接受请求不等于任务完成；实际构建状态与结果由扩展页面显示，不能把 accepted 当作成功安装。
编辑器用导出的 `open` 动作进入扩展管理页面。Lua 脚本不能直接调用安装或构建扩展管理动作。

依据官方 [ECJ BatchCompiler](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/compiler/batch/BatchCompiler.html)、
[JetBrains Kotlin in-process compilation](https://github.com/JetBrains/kotlin/blob/master/compiler/build-tools/kotlin-build-tools-impl/src/main/kotlin/org/jetbrains/kotlin/buildtools/internal/CompilationServiceImpl.kt)、
[Google D8 API](https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/D8.java)。

当前验证：真实 Java/Kotlin 插件模板编译、DEX 文件头、签名验证、payload 全部哈希、私钥不入包、两个项目的导入/路径边界、工具链篡改拒绝。
Android 编译器尚未在实体设备完成构建验证；桌面真实编译结果不代表手机工具链已兼容。
