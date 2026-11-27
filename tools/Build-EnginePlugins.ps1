#Requires -Version 7.0
param([switch]$SkipBuild, [string]$SigningKey,
    [ValidateSet('sensevoice-asr','piper-tts','streaming-asr','quick-text-tools','cloud-asr','cloud-tts','led-caption','kigusay-backup','cloud-agent','lua-runtime','native-builder','ui-debug')]
    [string[]]$Modules=@('sensevoice-asr','piper-tts','streaming-asr','cloud-asr','cloud-tts','led-caption','kigusay-backup','ui-debug'),
    [string]$PluginSourceRoot, [string]$AndroidSdk,
    [string]$WorkDirectory=(Join-Path $env:LOCALAPPDATA 'Temp/KIGTTSNEXT/plugin-package-work'))
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if(-not $PluginSourceRoot){$PluginSourceRoot=Join-Path (Split-Path $projectRoot) 'KIGTTS-Plugins'}
if(-not $AndroidSdk){$AndroidSdk=Join-Path $projectRoot '.tools/android-sdk'}
if(-not $SigningKey){$SigningKey=Join-Path $projectRoot '.tools/plugin-signing/private.der'}
if(-not (Test-Path -LiteralPath $SigningKey -PathType Leaf)){throw 'Pass an existing publisher key with -SigningKey; this wrapper never creates keys'}
$repositories=Get-Content -LiteralPath (Join-Path $projectRoot 'distribution/plugin-repositories.json') -Raw | ConvertFrom-Json
$output=Join-Path $projectRoot 'build/plugin-packages'
[IO.Directory]::CreateDirectory($output) | Out-Null
foreach($module in $Modules){
    $entry=$repositories | Where-Object module -eq $module
    $source=Join-Path $PluginSourceRoot $module
    $packager=Join-Path $source '.sdk/tools/Package-Plugin.ps1'
    if(-not (Test-Path -LiteralPath $packager -PathType Leaf)){throw "Clone $($entry.repository) to $source and initialize its SDK submodule"}
    & $packager -ProjectRoot $source -SigningKey $SigningKey -AndroidSdk $AndroidSdk -SkipBuild:$SkipBuild -WorkDirectory $WorkDirectory
    if($LASTEXITCODE -ne 0){throw "Independent build failed: $module"}
    $spec=Get-Content -LiteralPath (Join-Path $source 'plugin-spec.json') -Raw | ConvertFrom-Json
    $name="$($spec.id)-$($spec.version).kigplugin"
    $artifact=Join-Path $source "build/plugin-packages/$name"
    $destination=Join-Path $output $name
    if(Test-Path -LiteralPath $destination){
        if((Get-FileHash -LiteralPath $artifact).Hash -ne (Get-FileHash -LiteralPath $destination).Hash){throw "Refusing to overwrite immutable package $name"}
    }else{Copy-Item -LiteralPath $artifact -Destination $destination}
    Write-Output $destination
}
