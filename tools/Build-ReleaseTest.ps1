#Requires -Version 7.0
param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-17.0.17.10-hotspot'
if(-not $SkipBuild) {
    & .tools/gradle-8.13/bin/gradle.bat --gradle-user-home .gradle-user-home -PkigttsPreviewPackage=true :app:assembleRelease :app:lintRelease --console=plain
    if($LASTEXITCODE -ne 0){throw 'Release build failed'}
}
$buildTools=Join-Path $projectRoot '.tools/android-sdk/build-tools/36.0.0'
$unsigned=Join-Path $projectRoot 'app/build/outputs/apk/release/app-release-unsigned.apk'
$debug=Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
if ((& (Join-Path $buildTools 'aapt.exe') dump badging $unsigned | Select-Object -First 1) -notmatch "name='com\.kigtts\.next'") {
    throw 'Build with -PkigttsPreviewPackage=true before signing a preview APK'
}
$keystore=Join-Path $env:USERPROFILE '.android/debug.keystore'
$output=Join-Path $projectRoot 'app/build/outputs/apk/release-test'
[IO.Directory]::CreateDirectory($output) | Out-Null
$aligned=Join-Path $output 'app-release-aligned.apk'
$signed=Join-Path $output 'KIGTTSNEXT-release-localtest.apk'
# This is an optimized, non-debuggable Release signed ONLY for this local device test.
# Do not wire the development key into the production Release signing configuration.
& (Join-Path $buildTools 'zipalign.exe') -f -P 16 4 $unsigned $aligned
if($LASTEXITCODE -ne 0){throw 'APK alignment failed'}
& (Join-Path $buildTools 'apksigner.bat') sign --ks $keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $signed $aligned
if($LASTEXITCODE -ne 0){throw 'Local test signing failed'}
function Certificate([string]$Apk) {
    $verification=& (Join-Path $buildTools 'apksigner.bat') verify --print-certs $Apk
    if($LASTEXITCODE -ne 0){throw "Invalid APK signature: $Apk"}
    $digest=[regex]::Match(($verification -join "`n"),'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)').Groups[1].Value
    if(-not $digest){throw 'APK certificate digest missing'}
    return $digest
}
if((Certificate $signed) -ne (Certificate $debug)){throw 'Certificate differs from the existing Debug APK; do not install over user data'}
& (Join-Path $buildTools 'zipalign.exe') -c -P 16 4 $signed
if($LASTEXITCODE -ne 0){throw 'Signed APK alignment check failed'}
Write-Output $signed
Get-FileHash -LiteralPath $signed -Algorithm SHA256
