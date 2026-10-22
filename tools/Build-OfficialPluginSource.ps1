#Requires -Version 7.0
param([string]$OutputDirectory = '.tools/official-plugin-source', [string]$AndroidApk, [switch]$DevelopmentSigned)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$output = [IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
if (-not $output.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Output must stay within the workspace' }
[IO.Directory]::CreateDirectory($output) | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$homepage = 'https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Official-Plugins'
$publisher = 'b5927c76c21539ef99352707c215b3ce48f3fed937a6f995d39a3c677a052ab5'
$config = Get-Content (Join-Path $projectRoot 'distribution/official-packages.json') -Raw -Encoding utf8 | ConvertFrom-Json
$sdkApi = [int]([regex]::Match([IO.File]::ReadAllText((Join-Path $projectRoot '.sdk/plugin-api/src/main/kotlin/com/kigtts/plugin/api/Plugin.kt')), 'const val PLUGIN_API_VERSION = (\d+)').Groups[1].Value)
if ($sdkApi -le 0) { throw 'Missing host SDK version' }
function Read-ZipBytes($archive, [string]$name) {
    $entry = $archive.GetEntry($name)
    if (-not $entry) { throw "Missing archive entry: $name" }
    $stream = $entry.Open(); $buffer = [IO.MemoryStream]::new()
    try { $stream.CopyTo($buffer); return ,$buffer.ToArray() } finally { $stream.Dispose(); $buffer.Dispose() }
}
$packages = @(); $rows = @()
foreach ($item in $config) {
    if ($item.file -notmatch '^[a-z0-9.-]+\.kigplugin$') { throw 'Invalid package filename' }
    $file = Join-Path $projectRoot "build/plugin-packages/$($item.file)"
    $archive = [IO.Compression.ZipFile]::OpenRead($file)
    try {
        $raw = Read-ZipBytes $archive 'plugin.json'
        $spec = $utf8.GetString($raw) | ConvertFrom-Json
        $key = Read-ZipBytes $archive 'signature/public.der'
        $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($key)).ToLowerInvariant()
        if ($digest -ne $publisher) { throw "Unexpected publisher: $($spec.id)" }
        $rsa = [Security.Cryptography.RSA]::Create()
        try {
            $used = 0; $rsa.ImportSubjectPublicKeyInfo($key, [ref]$used)
            if (-not $rsa.VerifyData($raw, (Read-ZipBytes $archive 'signature/manifest.sig'), [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)) { throw 'Invalid package signature' }
        } finally { $rsa.Dispose() }
        foreach ($property in $spec.files.PSObject.Properties) {
            $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData((Read-ZipBytes $archive $property.Name))).ToLowerInvariant()
            if ($actual -ne $property.Value) { throw "Payload hash mismatch: $($spec.id)/$($property.Name)" }
        }
        $release = [ordered]@{
            version = $spec.version; versionCode = $spec.versionCode; format = 'NATIVE'
            sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
            bytes = (Get-Item -LiteralPath $file).Length; expectedPublisher = $publisher
            mirrors = @(@{ name = '魔搭官方源'; url = "$homepage/resolve/master/$($item.file)?view=false" })
            apiVersion = $spec.apiVersion; minSdk = $spec.minSdk; abis = @($spec.abis)
            notes = if ($item.notes) { $item.notes } else { '暂无更新说明。' }
            dependencies = if ($spec.dependencies) { $spec.dependencies } elseif ($item.dependencies) { $item.dependencies } else { @{} }
            permissions = @($spec.capabilities | Where-Object { $_ -ne 'ui' })
            permissionPurposes = if ($spec.permissionPurposes) { $spec.permissionPurposes } else { @{} }
        }
        $packages += [ordered]@{ id = $spec.id; name = $spec.name; summary = $item.summary; category = $item.category
            license = $spec.license; homepage = "https://github.com/KIGTTS/plugin-$($item.module)"
            icon = @{ symbol = if ($item.symbol) { $item.symbol } else { 'extension' } }
            description = if ($item.description) { $item.description } else { $item.summary }
            tags = @($item.tags); releases = @($release) }
        $rows += "| $($spec.name) | $($spec.version) | $($spec.apiVersion) | $($spec.license) |"
        Copy-Item -LiteralPath $file -Destination (Join-Path $output $item.file) -Force
    } finally { $archive.Dispose() }
}
$packages = @($packages | Group-Object { $_.id } | ForEach-Object {
    $package = $_.Group[0]
    $package.releases = @($_.Group | ForEach-Object { $_.releases })
    $package
})
$catalog = [ordered]@{ schemaVersion = 1; id = 'kigtts.official'; name = 'KIGTTS 官方插件源'
    description = '扩展朗读、聆听和创作体验，让常用工具触手可及。'; homepage = 'https://lht02.github.io/KPM/'
    icon = @{ symbol = 'inventory_2' }; assets = @{}
    readme = "## KPM 官方插件源`n`n在这里浏览语音、AI与创作工具，按自己的需要丰富KIGTTS。`n`n- 找到适合日常交流的朗读和聆听工具。`n- 让AI协助整理内容与动手创作。`n- 使用插件编辑器制作并分享自己的小工具。"
    updatedAt = [DateTimeOffset]::UtcNow.ToString('o'); packages = $packages }
[IO.File]::WriteAllText((Join-Path $output 'index.json'), ($catalog | ConvertTo-Json -Depth 15) + "`n", $utf8)
$readme = @"
---
license: mit
language:
- zh
tags:
- KIGTTS
- plugins
---
# KIGTTS 官方插件源

为 KIGTTS 添加朗读、聆听、创作与日常工具。可在软件的插件商店中浏览和安装。

源地址：$homepage/resolve/master/index.json?view=false

新版 KIGTTS 已内置这个源。安装前可以查看插件介绍、所需权限和许可证。

发布者 SHA-256：$publisher

| 插件 | 版本 | SDK API | 许可 |
| --- | --- | --- | --- |
$($rows -join "`n")

当前主程序为 API$sdkApi。插件所需版本见上表，旧版本仍可在商店中选择。系统声音包含在语音合成服务中；其它语音引擎需另行添加语音包。

网页添加指引与模板：https://lht02.github.io/KPM/

## 源码与许可证

本仓库清单与说明采用 MIT。各插件和第三方依赖分别遵守其包内 assets/licenses 中的 LICENSE/NOTICE；MIT 不改变 GPL 插件或其它依赖的许可。

项目源码与构建脚本：https://github.com/KIGTTS/KIGTTSNEXT

各插件已拆分为 KIGTTS 组织下的独立仓库，源清单的 homepage 指向对应源码与许可证。Piper 0.8.2 的独立源码归档：https://github.com/KIGTTS/plugin-piper-tts/archive/61e2befc6b2a87bcc5f15d1f19e0d53064793cf4.zip 。0.8.1 的独立源码归档：https://github.com/KIGTTS/plugin-piper-tts/archive/eeab25968990a0babda8c3e6902137c34b51f44b.zip 。0.8.0 的独立源码归档：https://github.com/KIGTTS/plugin-piper-tts/archive/0e9aedef9ddde90f1fe92762e53a4a4daf235c65.zip ，包含 native-source 下的 JNI、发音器、固定 eSpeak 源归档及构建配置；其余依赖的固定版本、源地址及许可见各插件 NOTICE.md。使用标准 Git 克隆时也请获取仓库声明的子模块与 LFS 文件。

源清单格式、依赖和镜像规则：https://github.com/KIGTTS/KIGTTSNEXT/blob/main/docs/package-store.md

发布准备：先构建签名 APK 和插件，再运行 tools/Build-OfficialPluginSource.ps1。上传输出目录的文件，避免上传整个工作区、私钥、模型和用户工程。
"@
[IO.File]::WriteAllText((Join-Path $output 'README.md'), $readme.Replace("`r`n", "`n") + "`n", $utf8)
Copy-Item -LiteralPath (Join-Path $projectRoot 'LICENSE') -Destination (Join-Path $output 'LICENSE') -Force
if ($AndroidApk) {
    $apk = (Resolve-Path -LiteralPath $AndroidApk).Path
    if (-not $apk.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetExtension($apk) -ne '.apk') { throw 'APK must be a workspace build artifact' }
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    $relative = "android/KIGTTSNEXT-api$sdkApi-$([DateTime]::UtcNow.ToString('yyyyMMdd'))-$($hash.Substring(0,12)).apk"
    [IO.Directory]::CreateDirectory((Join-Path $output 'android')) | Out-Null
    Copy-Item -LiteralPath $apk -Destination (Join-Path $output $relative) -Force
    $android = [ordered]@{ path = $relative; sha256 = $hash; bytes = (Get-Item -LiteralPath $apk).Length
        apiVersion = $sdkApi; version = '0.1.0-preview'; developmentSigning = [bool]$DevelopmentSigned; sourceCommit = (& git -C $projectRoot rev-parse HEAD) }
    [IO.File]::WriteAllText((Join-Path $output 'android/release.json'), ($android | ConvertTo-Json) + "`n", $utf8)
    $signing = if ($DevelopmentSigned) { '此预览版使用开发测试证书签名。覆盖安装需要与已装版本的签名一致，请先备份重要内容。' } else { '这是预览APK，请核对签名和当前安装版本。' }
    [IO.File]::AppendAllText((Join-Path $output 'README.md'), "`n## Android预览版`n`n[下载API$sdkApi 安卓APK]($homepage/resolve/master/$relative`?view=false)`n`n$signing`n`nSHA-256：$hash`n`n构建源码提交：$($android.sourceCommit)`n", $utf8)
}
Write-Output "Prepared $($packages.Count) signed packages in $output"
