# KIGTTS 内置语音识别服务

新增服务源码 MIT。服务只包含引擎管理、当前引擎选择与规范识别路由，不链接 sherpa、ONNX Runtime、SenseVoice 或其它引擎实现，不包含模型。

服务源码来自原独立项目 KIGTTS/plugin-recognition-service。字幕与语音输入需要明确选择已就绪的识别引擎；内置服务通过 RecognitionBroker 调用独立的 PROVIDER 引擎。流式模式保留中间结果；非流式模式先断句，再输出该句的最终识别结果。

服务配置与排序在 `appdata/Kigtts/plugin/plugindata/service.speech-recognition/config/`。各引擎模型、下载与其它资源仍在对应引擎自己的 Appdata，服务仅打开其声明的设置/识别包管理页面。MIT 完整许可见 LICENSE。
