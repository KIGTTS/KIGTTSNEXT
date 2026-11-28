#Requires -Version 7.0
param([Parameter(Mandatory)][string]$Device,[Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,32}$')][string]$Label,
    [ValidateRange(3,10)][int]$ColdStarts=5,[ValidateRange(1,10)][int]$NavigationCycles=4,[ValidateRange(2,30)][int]$Swipes=12,[switch]$SkipScroll)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$adb=Join-Path $root '.tools/android-sdk/platform-tools/adb.exe'
$bridge=Join-Path $PSScriptRoot 'Invoke-PluginManager.ps1'
$output=Join-Path $root (".tools/performance/$Label/"+[DateTime]::Now.ToString('yyyyMMdd-HHmmss'))
[IO.Directory]::CreateDirectory($output) | Out-Null
function Adb([string[]]$Arguments) {
    $text=& $adb -s $Device @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ($text -join "`n")}
    return ($text -join "`n")
}
function Save([string]$Name,[string]$Text) { [IO.File]::WriteAllText((Join-Path $output $Name),$Text,[Text.UTF8Encoding]::new($false)) }
function Page([string]$Name) {
    $null=& $bridge -Device $Device -Command navigate -Payload (@{page=$Name} | ConvertTo-Json -Compress) -TimeoutSeconds 30
    if($LASTEXITCODE -ne 0){throw 'Page navigation failed'}
    Start-Sleep -Milliseconds 400
}
function Back {
    $null=& $bridge -Device $Device -Command back -TimeoutSeconds 30
    if($LASTEXITCODE -ne 0){throw 'Page return failed'}
    Start-Sleep -Milliseconds 400
}
function Number([string]$Text,[string]$Pattern) {
    $match=[regex]::Match($Text,$Pattern)
    if(-not $match.Success){return $null}
    return [double]::Parse($match.Groups[1].Value,[Globalization.CultureInfo]::InvariantCulture)
}
function Graphics([string]$Scene) {
    $raw=Adb @('shell','dumpsys','gfxinfo','com.lhtstudio.kigtts.app','framestats');Save "$Scene-gfx.txt" $raw
    $frames=Number $raw 'Total frames rendered: (\d+)'
    if(-not $frames){throw "No rendered frames recorded for $Scene"}
    return [PSCustomObject]@{
        Scene=$Scene;Frames=$frames;JankyFrames=Number $raw 'Janky frames: (\d+)'
        JankyPercent=Number $raw 'Janky frames: \d+ \(([\d.]+)%\)'
        LegacyJankyPercent=Number $raw 'Janky frames \(legacy\): \d+ \(([\d.]+)%\)'
        P50Ms=Number $raw '(?m)^50th percentile: (\d+)ms';P90Ms=Number $raw '(?m)^90th percentile: (\d+)ms'
        P95Ms=Number $raw '(?m)^95th percentile: (\d+)ms';P99Ms=Number $raw '(?m)^99th percentile: (\d+)ms'
        GpuP95Ms=Number $raw '95th gpu percentile: (\d+)ms'
    }
}
function Screen([string]$Name) {
    $remote="/data/local/tmp/kigtts-perf-$Label-$Name.png"
    $null=Adb @('shell','screencap','-p',$remote)
    $null=Adb @('pull',$remote,(Join-Path $output "$Name.png"))
    $null=Adb @('shell','rm',$remote)
}
$start=[DateTimeOffset]::Now.ToString('o')
Save 'battery-start.txt' (Adb @('shell','dumpsys','battery'))
Save 'thermal-start.txt' (Adb @('shell','dumpsys','thermalservice'))
Save 'display.txt' (Adb @('shell','dumpsys','display'))
$size=Adb @('shell','wm','size');Save 'size.txt' $size
$dimensions=[regex]::Matches($size,'(\d+)x(\d+)')[-1]
$width=[int]$dimensions.Groups[1].Value;$height=[int]$dimensions.Groups[2].Value
$environment=@{Device=$Device;Sdk=Adb @('shell','getprop','ro.build.version.sdk');Model=Adb @('shell','getprop','ro.product.model')
    AlwaysFinishActivities=Adb @('shell','settings','get','global','always_finish_activities')
    PeakRefreshRate=Adb @('shell','settings','get','system','peak_refresh_rate');Size=$size;Density=Adb @('shell','wm','density')}
$packagePath=[regex]::Match((Adb @('shell','pm','path','com.lhtstudio.kigtts.app')),'package:([^\r\n]+)').Groups[1].Value
if(-not $packagePath){throw 'Installed APK path missing'}
$environment.InstalledApkSha256=(Adb @('shell','sha256sum',$packagePath)).Split(' ')[0]
# Process-cold first draw, not a device reboot / storage-cold launch / fully drawn metric.
# One warm-up stabilizes first-use costs; keep it in the raw report, outside the median.
$null=Adb @('shell','am','force-stop','com.kigtts.manager')
try { $samples=for($i=0;$i -le $ColdStarts;$i++) {
    $null=Adb @('shell','input','keyevent','KEYCODE_HOME')
    Start-Sleep -Milliseconds 500
    $null=Adb @('shell','am','force-stop','com.lhtstudio.kigtts.app')
    Start-Sleep -Milliseconds 500
    $existing=& $adb -s $Device shell pidof com.lhtstudio.kigtts.app
    if($existing){throw 'Application restarted in the background; this would not be a cold launch'}
    $raw=Adb @('shell','am','start','-W','-n','com.lhtstudio.kigtts.app/com.kigtts.next.MainActivity');Save "startup-$i.txt" $raw
    if($raw -notmatch 'Status: ok' -or $raw -notmatch 'LaunchState: COLD'){throw 'Launch was not a successful process-cold start'}
    Write-Host "$Label cold start $i/$ColdStarts"
    Start-Sleep -Milliseconds 1500
    [PSCustomObject]@{Warmup=($i -eq 0);TotalMs=Number $raw 'TotalTime: (\d+)';WaitMs=Number $raw 'WaitTime: (\d+)'}
} } finally {
    # Restore the companion's process after removing its auto-create binding from cold starts.
    # Preferences, monitoring settings, plugins and Appdata are never changed.
    $null=Adb @('shell','am','start','-W','-n','com.kigtts.manager/.ManagerActivity')
    $null=Adb @('shell','am','start','-W','-n','com.lhtstudio.kigtts.app/com.kigtts.next.MainActivity')
}
$snapshot=& $bridge -Device $Device -Command snapshot -TimeoutSeconds 30
if($LASTEXITCODE -ne 0){throw 'Plugin snapshot failed'}
Save 'plugins.json' ($snapshot -join "`n")
$plugins=($snapshot -join "`n") | ConvertFrom-Json
if(@($plugins.plugins | Where-Object { $_.enabled -and (-not $_.loaded -or $_.error) }).Count){throw 'Enabled plugin failed to load; benchmark would be misleading'}
Page 'home'
$processes=Adb @('shell','ps','-A','-o','PID,NAME')
$memory=foreach($line in ($processes -split "`n")) {
    if($line -match '^\s*(\d+)\s+(com\.kigtts\.next(?::plugin\d+)?)\s*$') {
        $processId=$Matches[1];$name=$Matches[2]
        $raw=Adb @('shell','dumpsys','meminfo',$processId);Save ("memory-"+$name.Replace(':','-')+'.txt') $raw
        [PSCustomObject]@{Process=$name;Pid=$processId;PssKiB=Number $raw 'TOTAL PSS:\s+(\d+)';RssKiB=Number $raw 'TOTAL RSS:\s+(\d+)'}
    }
}
$null=Adb @('shell','dumpsys','gfxinfo','com.lhtstudio.kigtts.app','reset')
for($i=1;$i -le $NavigationCycles;$i++) {
    Write-Host "$Label navigation $i/$NavigationCycles"
    Page 'settings';Page 'appearance';Back;Back;Page 'plugins';Back
}
$navigation=Graphics 'navigation'
if(-not $SkipScroll) {
Page 'settings';Page 'appearance';Screen 'scroll-start'
$null=Adb @('shell','dumpsys','gfxinfo','com.lhtstudio.kigtts.app','reset')
# Use actual content: the device intercepts swipes in the edge padding.
# Continuous vertical movement cancels clicks rather than tapping preferences.
$x=[int]($width*.5);$low=[int]($height*.8);$high=[int]($height*.25)
for($i=0;$i -lt $Swipes;$i++) {
    if($i%2 -eq 0){$from=$low;$to=$high}else{$from=$high;$to=$low}
    $null=Adb @('shell','input','swipe',"$x","$from","$x","$to",'400')
    Start-Sleep -Milliseconds 250
    if($i -eq 0){
        Screen 'scroll-after-first'
        Add-Type -AssemblyName System.Drawing
        $before=[Drawing.Bitmap]::new((Join-Path $output 'scroll-start.png'))
        $after=[Drawing.Bitmap]::new((Join-Path $output 'scroll-after-first.png'))
        try {
            $changed=0;$pixels=0
            for($y=[int]($height*.35);$y -lt [int]($height*.8);$y+=12) {
                for($sampleX=[int]($width*.15);$sampleX -lt [int]($width*.85);$sampleX+=12) {
                    $pixels++;if($before.GetPixel($sampleX,$y).ToArgb() -ne $after.GetPixel($sampleX,$y).ToArgb()){$changed++}
                }
            }
            if($changed/$pixels -lt .02){throw 'Native scroll did not visibly move; frame statistics cannot be reported as scrolling'}
        } finally {$before.Dispose();$after.Dispose()}
    }
}
$scroll=Graphics 'settings-scroll'
Back;Back
}
Save 'battery-end.txt' (Adb @('shell','dumpsys','battery'))
Save 'thermal-end.txt' (Adb @('shell','dumpsys','thermalservice'))
$ordered=@($samples | Where-Object { -not $_.Warmup } | Sort-Object TotalMs)
$report=@{Label=$Label;OutputDirectory=$output;Start=$start;End=[DateTimeOffset]::Now.ToString('o');Environment=$environment
    StartupSamples=$samples;ColdMedianMs=($ordered[[int][Math]::Floor(($ordered.Count-1)/2)].TotalMs+$ordered[[int][Math]::Floor($ordered.Count/2)].TotalMs)/2
    Memory=$memory;Graphics=@($navigation)+$(if(-not $SkipScroll){@($scroll)}else{@()});NavigationCycles=$NavigationCycles;Swipes=$(if($SkipScroll){0}else{$Swipes})
    ScrollIncluded=(-not $SkipScroll)
    Method='HOME; verify process gone; am start -W; warm-up excluded; bridge navigation; optional scroll requires visible pixel change; gfxinfo is not end-to-end FPS'}
$json=$report | ConvertTo-Json -Depth 8;Save 'summary.json' $json;$json
