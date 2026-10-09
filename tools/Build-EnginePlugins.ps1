#Requires -Version 7.0
param([switch]$SkipBuild, [string]$SigningKey,
    [ValidateSet('system-tts', 'sensevoice-asr', 'piper-tts', 'speech-service', 'streaming-asr')]
    [string[]]$Modules = @('system-tts', 'sensevoice-asr', 'piper-tts', 'speech-service', 'streaming-asr'),
    [string]$WorkDirectory = (Join-Path $env:LOCALAPPDATA 'Temp/KIGTTSNEXT/plugin-package-work'))
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
$env:JAVA_HOME = 'C:/Program Files/Microsoft/jdk-17.0.17.10-hotspot'
if (-not $SkipBuild) {
    $buildTasks = $Modules | ForEach-Object { ":engine-plugins:${_}:assembleDebug"; ":engine-plugins:${_}:collectPluginDependencies" }
    & .tools/gradle-8.13/bin/gradle.bat --gradle-user-home .gradle-user-home --console=plain @buildTasks
    if ($LASTEXITCODE -ne 0) { throw 'Plugin compilation failed' }
}
$utf8 = [Text.UTF8Encoding]::new($false)
if (-not $SigningKey) { $SigningKey = Join-Path $projectRoot '.tools/plugin-signing/private.der' }
$SigningKey = [IO.Path]::GetFullPath($SigningKey)
$rsa = [Security.Cryptography.RSA]::Create(3072)
if (Test-Path -LiteralPath $SigningKey) {
    $consumed = 0
    $rsa.ImportPkcs8PrivateKey([IO.File]::ReadAllBytes($SigningKey), [ref]$consumed)
} else {
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($SigningKey)) | Out-Null
    [IO.File]::WriteAllBytes($SigningKey, $rsa.ExportPkcs8PrivateKey())
    Write-Output 'Created a local publisher key. Keep it private and retain it for future updates.'
}
$outputRoot = Join-Path $projectRoot 'build/plugin-packages'
[IO.Directory]::CreateDirectory($outputRoot) | Out-Null
function Expand-PluginAar([string]$Aar, [string]$Payload, [string]$Scratch, [string]$Prefix, [string]$NativeAbi = '') {
    $archive = [IO.Compression.ZipFile]::OpenRead($Aar)
    try {
        $jars = @()
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -eq 'classes.jar') {
                $jar = Join-Path $Scratch "$Prefix-classes.jar"
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $jar, $true)
                $jars += $jar
            }
            if ($entry.FullName -match '^jni/([^/]+)/([^/]+\.so)$') {
                if ($NativeAbi -and $Matches[1] -ne $NativeAbi) { continue }
                $target = Join-Path $Payload "lib/$($Matches[1])/$($Matches[2])"
                [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
                if (Test-Path -LiteralPath $target) {
                    throw "Duplicate native library: $($entry.FullName)"
                }
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $false)
            }
        }
        return $jars
    } finally { $archive.Dispose() }
}
foreach ($module in $Modules) {
    $moduleRoot = Join-Path $projectRoot "engine-plugins/$module"
    $spec = Get-Content -LiteralPath (Join-Path $moduleRoot 'plugin-spec.json') -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable
    $scratch = [IO.Path]::GetFullPath((Join-Path $WorkDirectory "$module/$([Guid]::NewGuid())"))
    $nativeAbi = if ($module -eq 'piper-tts') { 'arm64-v8a' } else { '' }
    $payload = Join-Path $scratch 'payload'
    [IO.Directory]::CreateDirectory((Join-Path $payload 'code')) | Out-Null
    $jars = @(Expand-PluginAar (Join-Path $moduleRoot "build/outputs/aar/$module-debug.aar") $payload $scratch $module $nativeAbi)
    if ($module -eq 'sensevoice-asr') {
        $jars += @(Expand-PluginAar (Join-Path $moduleRoot 'libs/sherpa-onnx-1.12.38-noort.aar') $payload $scratch 'sherpa')
    }
    if ($module -eq 'streaming-asr') {
        $jars += @(Expand-PluginAar (Join-Path $projectRoot 'engine-plugins/sensevoice-asr/libs/sherpa-onnx-1.12.38-noort.aar') $payload $scratch 'sherpa')
    }
    foreach ($dependency in @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'build/plugin-dependencies') -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^(onnxruntime-android|xz-|system-support|commons-compress|commons-io|commons-codec|commons-lang3)' })) {
        if ($dependency.Extension -eq '.aar') { $jars += @(Expand-PluginAar $dependency.FullName $payload $scratch $dependency.BaseName $nativeAbi) }
        elseif ($dependency.Extension -eq '.jar') { $jars += $dependency.FullName }
    }
    $assetsRoot = Join-Path $moduleRoot 'src/main/assets'
    if (Test-Path -LiteralPath $assetsRoot) {
        [IO.Directory]::CreateDirectory((Join-Path $payload 'assets')) | Out-Null
        Copy-Item -Path (Join-Path $assetsRoot '*') -Destination (Join-Path $payload 'assets') -Recurse
    }
    if ($module -eq 'piper-tts') {
        [IO.Directory]::CreateDirectory((Join-Path $payload 'lib/arm64-v8a')) | Out-Null
        Copy-Item -Path (Join-Path $moduleRoot 'src/main/jniLibs/arm64-v8a/*.so') -Destination (Join-Path $payload 'lib/arm64-v8a')
        # Keep only complete ABIs when extracting dependencies; the ported bridge is arm64.
    }
    & .tools/android-sdk/build-tools/36.0.0/d8.bat --min-api 26 --lib .tools/android-sdk/platforms/android-36/android.jar --output (Join-Path $payload 'code') @jars
    if ($LASTEXITCODE -ne 0) { throw "DEX compilation failed: $module" }
    $licenses = Join-Path $payload 'assets/licenses'
    [IO.Directory]::CreateDirectory($licenses) | Out-Null
    $licenseName = if ($spec['license'] -eq 'MIT') { 'KIGTTS-MIT.txt' } else { 'KIGTTS-GPL-3.0.txt' }
    Copy-Item -LiteralPath (Join-Path $moduleRoot 'LICENSE') -Destination (Join-Path $licenses $licenseName)
    Copy-Item -LiteralPath (Join-Path $moduleRoot 'NOTICE.md') -Destination (Join-Path $licenses 'NOTICE.md')
    if ($module -eq 'sensevoice-asr') {
        Copy-Item -LiteralPath (Join-Path $moduleRoot 'licenses/sherpa-onnx.txt') -Destination $licenses
        Copy-Item -LiteralPath (Join-Path $moduleRoot 'licenses/onnxruntime.txt') -Destination $licenses
    }
    if (Test-Path -LiteralPath (Join-Path $moduleRoot 'licenses')) {
        Copy-Item -Path (Join-Path $moduleRoot 'licenses/*') -Destination $licenses -Recurse
    }
    $spec['abis'] = @(Get-ChildItem -LiteralPath (Join-Path $payload 'lib') -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name)
    $hashes = [ordered]@{}
    Get-ChildItem -LiteralPath $payload -File -Recurse | Sort-Object FullName | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($payload, $_.FullName).Replace('\', '/')
        $hashes[$relative] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $spec['files'] = $hashes
    $raw = $utf8.GetBytes(($spec | ConvertTo-Json -Depth 12 -Compress))
    [IO.File]::WriteAllBytes((Join-Path $payload 'plugin.json'), $raw)
    $signatureRoot = Join-Path $payload 'signature'
    [IO.Directory]::CreateDirectory($signatureRoot) | Out-Null
    [IO.File]::WriteAllBytes((Join-Path $signatureRoot 'public.der'), $rsa.ExportSubjectPublicKeyInfo())
    [IO.File]::WriteAllBytes((Join-Path $signatureRoot 'manifest.sig'), $rsa.SignData($raw, [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1))
    $result = Join-Path $outputRoot "$($spec.id)-$($spec.version).kigplugin"
    if (Test-Path -LiteralPath $result) { Remove-Item -LiteralPath $result }
    [IO.Compression.ZipFile]::CreateFromDirectory($payload, $result, [IO.Compression.CompressionLevel]::Optimal, $false)
    Write-Output "$result ($((Get-Item -LiteralPath $result).Length) bytes)"
}
$rsa.Dispose()
