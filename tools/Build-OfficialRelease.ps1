#Requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$SigningBackup,
    [string]$SevenZip = 'C:/Program Files/7-Zip/7z.exe',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$archive = (Resolve-Path -LiteralPath $SigningBackup).Path
$expectedCertificate = '3193af87840d767843e42770c92823b20df10b5edf03a7d618ce9f4da0f5e197'
$buildTools = Join-Path $root '.tools/android-sdk/build-tools/36.0.0'
if (-not $SkipBuild) {
    & "$root/gradlew.bat" --project-dir $root --gradle-user-home "$root/.gradle-user-home" :app:assembleRelease --console=plain
    if ($LASTEXITCODE) { throw 'Official Release compilation failed' }
}
$unsigned = Join-Path $root 'app/build/outputs/apk/release/app-release-unsigned.apk'
$badging = (& "$buildTools/aapt.exe" dump badging $unsigned | Select-Object -First 1)
if ($LASTEXITCODE -or $badging -notmatch "name='com\.lhtstudio\.kigtts\.app' versionCode='10000' versionName='1\.0\.0'") {
    throw 'APK is not the 1.0.0 production build'
}

$scratch = [IO.Directory]::CreateTempSubdirectory('kigtts-sign-').FullName
$certificate = Join-Path $scratch 'KIGTTS_Android正式版-certificate.cer'
$pem = Join-Path $scratch 'KIGTTS-Android-正式版-未加密私钥-PKCS8.pem'
$key = Join-Path $scratch 'signing-key.pkcs8'
$aligned = Join-Path $scratch 'aligned.apk'
$signed = Join-Path $scratch 'signed.apk'
try {
    $entries = @('KIGTTS_Android正式版\KIGTTS_Android正式版-certificate.cer',
        'KIGTTS_Android正式版\KIGTTS-Android-正式版-未加密私钥-PKCS8.pem')
    & $SevenZip e -y -bd -bb0 "-o$scratch" $archive @entries *> $null
    if ($LASTEXITCODE) { throw 'Cannot read the production signing entries' }
    $cert = [Security.Cryptography.X509Certificates.X509Certificate2]::new($certificate)
    try {
        if ($cert.GetCertHashString([Security.Cryptography.HashAlgorithmName]::SHA256) -ne $expectedCertificate) {
            throw 'This certificate does not match the original official Android release'
        }
    } finally { $cert.Dispose() }
    $rsa = [Security.Cryptography.RSA]::Create()
    try {
        $rsa.ImportFromPem([IO.File]::ReadAllText($pem))
        $bytes = $rsa.ExportPkcs8PrivateKey()
        try { [IO.File]::WriteAllBytes($key, $bytes) } finally { [Array]::Clear($bytes) }
    } finally { $rsa.Dispose(); [IO.File]::Delete($pem) }

    & "$buildTools/zipalign.exe" -f -P 16 4 $unsigned $aligned
    if ($LASTEXITCODE) { throw 'APK alignment failed' }
    & "$buildTools/apksigner.bat" sign --key $key --cert $certificate --out $signed $aligned
    if ($LASTEXITCODE) { throw 'APK signing failed' }
    $details = & "$buildTools/apksigner.bat" verify --print-certs $signed
    if ($LASTEXITCODE -or -not ($details -match "Signer #1 certificate SHA-256 digest: $expectedCertificate")) {
        throw 'Signed APK verification failed'
    }
    $directory = Join-Path $root 'app/build/outputs/apk/official'
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    $output = Join-Path $directory 'KIGTTS-1.0.0.apk'
    Copy-Item -LiteralPath $signed -Destination $output -Force
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    foreach ($file in @($pem, $key, $certificate, $aligned, $signed, "$signed.idsig")) { [IO.File]::Delete($file) }
    [IO.Directory]::Delete($scratch)
}
