#Requires -Version 7.0
param([Parameter(Mandatory)]$Catalog, [Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../ARTS/PluginStore'))
$manifest = Get-Content (Join-Path $root 'index.json') -Raw -Encoding utf8 | ConvertFrom-Json
$designs = @{}
foreach ($entry in $manifest.packages) { $designs[$entry.id] = $entry }
foreach ($package in $Catalog.packages) {
    $entry = $designs[$package.id]
    if (-not $entry -or $entry.images.Count -lt 3 -or $entry.images.Count -gt 8) { throw "Missing artwork: $($package.id)" }
    $thumbnails = @()
    foreach ($image in $entry.images) {
        $file = [IO.Path]::GetFullPath((Join-Path $root $image.file))
        if (-not $file.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Artwork path escapes its folder' }
        $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $image.sha256 -or (Get-Item -LiteralPath $file).Length -ne $image.bytes -or $image.bytes -gt 8MB -or $image.width -le 0 -or $image.height -le 0) { throw "Invalid artwork: $($image.file)" }
        $stem = [IO.Path]::GetFileNameWithoutExtension($file)
        $extension = [IO.Path]::GetExtension($file)
        $key = "preview-$($package.id)-$stem"
        if ($key -notmatch '^[a-z0-9][a-z0-9._-]{0,63}$' -or $extension -notin '.svg', '.png') { throw 'Invalid artwork identifier or type' }
        $relative = "artwork/$($package.id)-$stem-$($hash.Substring(0,12))$extension"
        Copy-Item -LiteralPath $file -Destination (Join-Path $OutputDirectory $relative) -Force
        $Catalog.assets[$key] = [ordered]@{
            url = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Official-Plugins/resolve/master/$relative`?view=false"
            sha256 = $hash; bytes = $image.bytes; alt = $image.alt; width = $image.width; height = $image.height
        }
        $thumbnails += $key
    }
    $package.thumbnails = $thumbnails
}
if ($designs.Count -ne $Catalog.packages.Count) { throw 'Artwork and published plugins must match' }
Write-Output "Registered $($Catalog.packages.Count) plugin galleries"
