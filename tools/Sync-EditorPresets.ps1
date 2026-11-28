#Requires -Version 7.0
param([Parameter(Mandatory)][string]$TemplateRoot)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $TemplateRoot).Path
$assetRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'app/src/main/assets/editor-presets'
[IO.Directory]::CreateDirectory($assetRoot) | Out-Null
$guideRoot = Join-Path $assetRoot 'guides'
[IO.Directory]::CreateDirectory($guideRoot) | Out-Null
foreach ($guide in @('plugin-template.md','plugin-ui.md','quick-text-web.md','contracts.md')) {
    Copy-Item -LiteralPath (Join-Path $source "docs/$guide") -Destination (Join-Path $guideRoot $guide)
}
& "$source/scripts/Sync-CatalogWeb.ps1" -Check
& "$source/scripts/Check-Templates.ps1"
$revision = & git -C $source rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the template source revision' }
$nativeSpec = Get-Content -Raw -LiteralPath (Join-Path $source 'templates/native-java-smoke/plugin-spec.json') | ConvertFrom-Json
$catalogApi = [int]$nativeSpec.apiVersion
if ($catalogApi -lt 1) { throw 'Cannot identify the template SDK version' }
$presets = foreach ($entry in @(
    @{Id='lua-catalog'; Folder='lua-smoke'; Manifest='manifest.json'; Lang='lua'; Owner='runtime.lua'},
    @{Id='native-catalog'; Folder='native-java-smoke'; Manifest='plugin-spec.json'; Lang='java'; Owner='tools.native-builder'}
)) {
    $template = "templates/$($entry.Folder)"
    $manifest = Get-Content -Raw -LiteralPath (Join-Path $source "$template/$($entry.Manifest)") | ConvertFrom-Json
    & "$source/scripts/Build-SourceBundle.ps1" -Template $template | Write-Host
    $original = Join-Path $source "build/$($manifest.id)-$($manifest.version).source.zip"
    $archive = "$($entry.Id).zip"
    Copy-Item -LiteralPath $original -Destination (Join-Path $assetRoot $archive)
    [ordered]@{ id=$entry.Id; language=$entry.Lang; owner=$entry.Owner; archive=$archive; version=$manifest.version;
        apiVersion=$catalogApi; name=$manifest.name; exampleId=$manifest.id; manifest=$entry.Manifest;
        sha256=(Get-FileHash -LiteralPath $original).Hash.ToLowerInvariant() }
}
$index = [ordered]@{ schema=1; sourceRevision=$revision; items=@($presets) } | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText((Join-Path $assetRoot 'index.json'), "$index`n")
