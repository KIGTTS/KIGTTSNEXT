# 增量流式识别插件

`engine.streaming-asr-0.7.0.kigplugin` 是独立的 Android DEX/JNI 插件，API8，源码 MIT。引擎、识别包格式、下载、端点与语言选择不进入主程序。现有 SenseVoice 继续提供完整录音与分段离线识别；本插件声明 `StreamingKind.INCREMENTAL`，使用固定 sherpa-onnx 1.12.38 的在线识别器。

## 使用

安装、启用 [语音识别服务](recognition-service.md) 与流式识别引擎；在服务中选用该独立引擎，并进入其“流式识别包”页面管理模型。主界面通过服务调用引擎。

- 下载识别包：目录提供中文轻量、英文轻量、中英双语三个固定官方模型包，下载按预先核验的 SHA-256 校验。可从自定义 HTTPS 地址下载其它在线包，需提供可信 SHA-256。
- 导入识别包：支持 `.kigasr`、ZIP、官方 tar.bz2 和 tar.gz。可同时保留多个语言包，选用当前识别包，按语言筛选、置顶与拖动排序。
- 条目详情支持名称、备注和语言说明修改、更新、导出与二次确认删除。修改或切换模型前需停止识别；文件更新先验证新包，再交换目录，保留包 ID、选择、名称、备注、置顶与顺序。
- 单语言包识别对应语言；双语模型可在同一选用包中识别中文和英文。语言标签仅用于管理与筛选，不能为模型增加不支持的语言。`RecognitionConfig.language` 不会强行修改选用模型的词表。

所有下载、模型、外部文件、暂存、配置及导出都在 `appdata/Kigtts/plugin/plugindata/engine.streaming-asr/`。模型没有打入主 APK、插件包或 Git。模型与许可证按发布者原样保留。

## 支持的模型与包格式

支持在线 Transducer（Zipformer/Zipformer2/Conformer/LSTM）、在线 Paraformer、Zipformer CTC 与 NeMo CTC。官方在线包会识别 streaming 目录、tokens.txt 与模型文件，优先采用 int8；其它布局需要显式清单。不得把 SenseVoice、Whisper 等离线模型当成在线模型导入。

新增语言通过导入相应在线识别包扩展，不要求主程序更新。官方 Transducer 模型列表见 [sherpa-onnx 在线模型说明](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/index.html)，固定版本适配接口见 [OnlineRecognizer](https://github.com/k2-fsa/sherpa-onnx/blob/v1.12.38/sherpa-onnx/kotlin-api/OnlineRecognizer.kt)。其它语言是否可识别取决于模型与固定运行库的兼容性；首轮实解码仅覆盖中文、英文和中英双语 Transducer。

`.kigasr` 是 ZIP，根目录包含 UTF-8 `asr-pack.json` 与模型资源。也可导入含一个嵌套清单的 ZIP；安装时规范化为一个根清单。示例：

```json
{
  "format": 1,
  "name": "中英双语识别",
  "languages": ["zh", "en"],
  "architecture": "TRANSDUCER",
  "files": {
    "encoder": "encoder.int8.onnx",
    "decoder": "decoder.onnx",
    "joiner": "joiner.int8.onnx",
    "tokens": "tokens.txt"
  },
  "modelType": "",
  "sampleRate": 16000,
  "remark": "",
  "pinned": false,
  "order": 0
}
```

`architecture` 可为 `TRANSDUCER`、`PARAFORMER`、`ZIPFORMER_CTC`、`NEMO_CTC`。Paraformer 使用 encoder/decoder/tokens；两种 CTC 使用 model/tokens。`modelType` 留空时按 ONNX 模型自身元数据识别。仅支持 CPU；线程 1–4，默认 2。单声道 PCM16，采样率取包声明，默认 16 kHz。

压缩包限制解压总大小 2 GiB、最多 10,000 条目，拒绝越界、重复路径、tar 链接与特殊文件。不完整模型不会替换既有包。导出是完整模型包，临时文件在本插件 tmp，用户保存或取消后由宿主清理。

## 流式行为与验证

每次输入持续送入同一个在线流，解码所有已就绪块，从模型当前假设产生变化的 Partial；端点输出非空 Final 并重置当前句，继续接收后续语音。输入结束补右上下文并调用 inputFinished，冲刷尾句。Paraformer 端点/EOF 补 0.8 秒，其它 EOF 补 0.3 秒静音。取消、格式错误或失败均释放流与识别器。完整录音也使用相同在线后端，但不启用端点，返回一个完整结果。没有伪造中间结果或等待整句再离线识别。

本地专项覆盖中间事件早于 EOF、端点重置、不重复尾句、取消后再次识别、空输入/静音、格式错误、模型并存/选择/排序/置顶、更新回滚、导出回导、路径拒绝及下载校验/范围。主 APK 扫描确认没有引擎实现、模型或 `.kigplugin` 资产。

Windows CPU 使用同版本 sherpa-onnx 实际解码官方固定录音：中文轻量与英文轻量分别产生 24 / 30 次 EOF 前中间结果，并各输出两句最终文本；同一中英双语包也在中文与英文录音上产生中间和最终结果。小双语模型的中文录音有明显漏词，英文也有错词；功能链路通过不代表精度验收。首次出现中间结果的音频位置约 0.8–2.4 秒，不是 Android 延迟测量。

可复现实解码：先将三个官方目录包下载到对应 Windows 插件 Appdata/downloads，再用安装 `sherpa-onnx==1.12.38` / NumPy 的 Python 3.12 运行 `tools/Verify-StreamingModels.py --data-dir <对应插件 Appdata>`。解析、测试音频、规范包与 JSON 报告仍在该插件目录。

`StreamingRecognitionDeviceTest` 验证实际 Android DEX/JNI、中文/英文包共存、EOF 前 Partial、取消后重用与完整录音。需要将主应用和所有现有插件更新为 API8；传入 `zhPack` / `zhWav` / `enPack` / `enWav`，四个路径都必须位于手机本插件数据目录。测试会保留安装的流式插件及导入包。当前 ADB 没有连接设备，本轮没有执行该真机检查，也没有覆盖手机仍可用的 API3。
