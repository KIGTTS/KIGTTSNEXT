#Requires -Version 7.0
param([switch]$SkipBuild, [string]$SigningKey)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
$env:JAVA_HOME = 'C:/Program Files/Microsoft/jdk-17.0.17.10-hotspot'
if (-not $SkipBuild) {
    & .tools/gradle-8.13/bin/gradle.bat --gradle-user-home .gradle-user-home --console=plain :engine-plugins:system-tts:assembleDebug :engine-plugins:sensevoice-asr:assembleDebug :engine-plugins:sensevoice-asr:collectPluginDependencies
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
function Expand-PluginAar([string]$Aar, [string]$Payload, [string]$Scratch, [string]$Prefix) {
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
foreach ($module in @('system-tts', 'sensevoice-asr')) {
    $moduleRoot = Join-Path $projectRoot "engine-plugins/$module"
    $spec = Get-Content -LiteralPath (Join-Path $moduleRoot 'plugin-spec.json') -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable
    $scratch = Join-Path $projectRoot ".tools/plugin-package-work/$module/$([Guid]::NewGuid())"
    $payload = Join-Path $scratch 'payload'
    [IO.Directory]::CreateDirectory((Join-Path $payload 'code')) | Out-Null
    $jars = @(Expand-PluginAar (Join-Path $moduleRoot "build/outputs/aar/$module-debug.aar") $payload $scratch $module)
    if ($module -eq 'sensevoice-asr') {
        $jars += @(Expand-PluginAar (Join-Path $moduleRoot 'libs/sherpa-onnx-1.12.38-noort.aar') $payload $scratch 'sherpa')
        $ort = Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'build/plugin-dependencies') -Filter 'onnxruntime-android-1.24.3.aar' | Select-Object -First 1
        if (-not $ort) { throw 'Missing ONNX Runtime; run collectPluginDependencies' }
        $jars += @(Expand-PluginAar $ort.FullName $payload $scratch 'ort')
    }
    & .tools/android-sdk/build-tools/36.0.0/d8.bat --min-api 26 --lib .tools/android-sdk/platforms/android-36/android.jar --output (Join-Path $payload 'code') @jars
    if ($LASTEXITCODE -ne 0) { throw "DEX compilation failed: $module" }
    $licenses = Join-Path $payload 'assets/licenses'
    [IO.Directory]::CreateDirectory($licenses) | Out-Null
    Copy-Item -LiteralPath (Join-Path $moduleRoot 'LICENSE') -Destination (Join-Path $licenses 'KIGTTS-GPL-3.0.txt')
    Copy-Item -LiteralPath (Join-Path $moduleRoot 'NOTICE.md') -Destination (Join-Path $licenses 'NOTICE.md')
    if ($module -eq 'sensevoice-asr') {
        Copy-Item -LiteralPath (Join-Path $moduleRoot 'licenses/sherpa-onnx.txt') -Destination $licenses
        Copy-Item -LiteralPath (Join-Path $moduleRoot 'licenses/onnxruntime.txt') -Destination $licenses
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
