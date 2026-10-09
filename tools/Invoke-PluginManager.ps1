#Requires -Version 7.0
param([ValidateSet('snapshot','inspect','install','enable','restart','uninstall','refresh','resource','execute','tts-test','present','select-media','remove-media')]
    [string]$Command='snapshot',[string]$Payload='{}',[string]$File,[string]$Device,[int]$TimeoutSeconds=120)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$targetArgs=if($Device){@('-s',$Device)}else{@()}
function Invoke-Adb([string[]]$Arguments) {
    $lines=& $adb @targetArgs @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ($lines -join "`n")}
    return ($lines -join "`n")
}
function Read-Control([string]$Method,[string]$Argument) {
    $output=Invoke-Adb -Arguments @('shell','content','call','--uri','content://com.kigtts.manager.control','--method',$Method,'--arg',$Argument)
    $start=$output.IndexOf('result=')
    if($start -lt 0 -or -not $output.TrimEnd().EndsWith('}]')){throw "Invalid control response: $output"}
    $value=$output.TrimEnd().Substring($start+7)
    return $value.Substring(0,$value.Length-2) | ConvertFrom-Json -AsHashtable
}
$managerPid=& $adb @targetArgs shell pidof com.kigtts.manager
if(-not $managerPid){$null=Invoke-Adb -Arguments @('shell','am','start','-W','-n','com.kigtts.manager/.ManagerActivity')}
if($Command -in @('present','select-media','remove-media')) {
    $null=Invoke-Adb -Arguments @('shell','am','start','-W','-n','com.kigtts.next/.MainActivity')
}
$request=@{command=$Command;payload=($Payload | ConvertFrom-Json -AsHashtable)}
if($File) {
    $source=(Resolve-Path -LiteralPath $File).Path
    $name='transfer-'+[Guid]::NewGuid().ToString('N')+'.bin'
    $root='/sdcard/Android/data/com.kigtts.manager/files/appdata/Kigtts/plugin-manager/inbox'
    $null=Invoke-Adb -Arguments @('shell','mkdir','-p',$root)
    $null=Invoke-Adb -Arguments @('push',$source,"$root/$name")
    $request.file=$name
}
$encoded=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($request | ConvertTo-Json -Depth 30 -Compress)))
$job=(Read-Control 'enqueue' $encoded).job
$deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
while([DateTime]::UtcNow -lt $deadline) {
    $result=Read-Control 'job' $job
    if($result.state -eq 'done') {
        if($request.file){$null=Invoke-Adb -Arguments @('shell','rm',"$root/$($request.file)")}
        if(-not $result.ok){throw $result.error}
        $result.data | ConvertTo-Json -Depth 40
        exit 0
    }
    if($result.state -eq 'missing'){throw "Manager lost job $job; inspect current state before retrying."}
    Start-Sleep -Milliseconds 500
}
throw "Job $job has unknown completion. Query its state before retrying; transferred file is retained."
