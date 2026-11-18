#Requires -Version 7.0
param([Parameter(Mandatory)][System.Collections.IDictionary]$Catalog)
$ErrorActionPreference = 'Stop'

$translations = Get-Content (Join-Path $PSScriptRoot '../distribution/plugin-catalog-locales.json') -Raw -Encoding utf8 |
    ConvertFrom-Json -AsHashtable

if (-not $Catalog.localizations) { $Catalog.localizations = [ordered]@{} }
foreach ($locale in $translations.source.Keys) {
    $Catalog.localizations[$locale] = $translations.source[$locale]
}

foreach ($package in $Catalog.packages) {
    $variants = $translations.packages[$package.id]
    if (-not $variants) { continue }
    if (-not $package.localizations) { $package.localizations = [ordered]@{} }
    $versions = @($package.releases.version)
    foreach ($locale in $variants.Keys) {
        if (-not $package.localizations[$locale]) { $package.localizations[$locale] = [ordered]@{} }
        $target = $package.localizations[$locale]
        $variant = $variants[$locale]
        foreach ($field in @('name', 'summary', 'description', 'tags')) {
            $target[$field] = $variant[$field]
        }
        $notes = [ordered]@{}
        foreach ($version in $versions) {
            if ($variant.releaseNotes.Contains($version)) {
                $notes[$version] = $variant.releaseNotes[$version]
            }
        }
        $target['releaseNotes'] = $notes
    }
}
