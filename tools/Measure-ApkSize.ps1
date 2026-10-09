#Requires -Version 7.0
param([Parameter(Mandatory)][string[]]$Apk)
$ErrorActionPreference='Stop'
foreach($path in $Apk) {
    $file=Get-Item -LiteralPath $path
    $zip=[IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $entries=foreach($entry in $zip.Entries) {
            $group=switch -Regex ($entry.FullName) {
                '^classes\d*\.dex$' {'dex';break}
                '^lib/' {'native';break}
                '^assets/' {'assets';break}
                '^(res/|resources\.arsc$)' {'resources';break}
                default {'metadata'}
            }
            [PSCustomObject]@{Group=$group;Packed=$entry.CompressedLength;Raw=$entry.Length}
        }
        [PSCustomObject]@{
            Apk=$file.FullName;Bytes=$file.Length;MiB=[Math]::Round($file.Length/1MB,3)
            Sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
            Groups=@($entries | Group-Object Group | ForEach-Object {
                [PSCustomObject]@{Group=$_.Name;Entries=$_.Count;PackedBytes=($_.Group.Packed | Measure-Object -Sum).Sum;RawBytes=($_.Group.Raw | Measure-Object -Sum).Sum}
            })
        } | ConvertTo-Json -Depth 5
    } finally { $zip.Dispose() }
}
