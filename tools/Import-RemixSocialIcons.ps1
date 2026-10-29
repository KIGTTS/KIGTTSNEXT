#Requires -Version 7.0
param([string]$Revision = '9fb7967c0a4c09910161192bde99efd3df09f5eb')
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$utf8 = [Text.UTF8Encoding]::new($false)
$icons = @('wechat-fill','qq-fill','weibo-fill','bilibili-fill','tiktok-fill','twitter-x-fill',
    'twitter-fill','telegram-fill','discord-fill','facebook-fill','instagram-fill','youtube-fill',
    'github-fill','line-fill','whatsapp-fill','zhihu-fill','linkedin-fill','mastodon-fill',
    'bluesky-fill','threads-fill','reddit-fill','steam-fill','snapchat-fill',
    'douban-fill','baidu-fill','wechat-2-fill','wechat-channels-fill','netease-cloud-music-fill',
    'spotify-fill','soundcloud-fill','twitch-fill','vimeo-fill','yuque-fill','medium-fill','tumblr-fill',
    'wordpress-fill','gitlab-fill','gitee-fill','stack-overflow-fill','codepen-fill','product-hunt-fill',
    'notion-fill','zcool-fill','figma-fill','dribbble-fill','behance-fill','pinterest-fill','unsplash-fill',
    'flickr-fill','patreon-fill','paypal-fill')
$sources = @($icons | ForEach-Object { "icons/Logos/$_.svg" }) + @(
    'icons/Business/mail-line.svg','icons/Business/links-line.svg','icons/Document/book-2-fill.svg',
    'icons/Media/film-fill.svg','icons/Weather/flashlight-fill.svg','icons/Media/music-2-fill.svg',
    'icons/Media/mic-fill.svg','icons/Media/headphone-fill.svg','icons/Document/article-fill.svg',
    'icons/Development/code-s-slash-fill.svg','icons/Media/image-fill.svg','icons/User & Faces/user-star-fill.svg',
    'icons/Design/brush-fill.svg','icons/Finance/hand-heart-fill.svg','icons/Food/cup-fill.svg')
$rows = @()
foreach ($source in $sources) {
    $url = "https://raw.githubusercontent.com/Remix-Design/RemixIcon/$Revision/$source"
    $raw = (Invoke-WebRequest -Uri $url -MaximumRetryCount 2).Content
    $svg = [xml]$raw
    if ($svg.svg.viewBox -ne '0 0 24 24') { throw "Unexpected icon viewbox: $source" }
    $paths = @($svg.SelectNodes('//*[local-name()="path"]') | Where-Object { $_.fill -ne 'none' })
    if ($paths.Count -eq 0) { throw "Missing icon geometry: $source" }
    $xml = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">' + "`n"
    foreach ($path in $paths) { $xml += '    <path android:fillColor="#FF000000" android:pathData="' + $path.d + '"/>' + "`n" }
    $xml += "</vector>`n"
    $name = 'ic_remix_' + [IO.Path]::GetFileNameWithoutExtension($source).Replace('-', '_') + '.xml'
    [IO.File]::WriteAllText((Join-Path $root "app/src/main/res/drawable/$name"), $xml, $utf8)
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($raw))).ToLowerInvariant()
    $rows += [ordered]@{ resource = $name; source = $source; sha256 = $hash }
}
$license = (Invoke-WebRequest "https://raw.githubusercontent.com/Remix-Design/RemixIcon/$Revision/License").Content
[IO.File]::WriteAllText((Join-Path $root 'app/src/main/assets/licenses/Remix-Icon-License.txt'), $license, $utf8)
$manifest = [ordered]@{ upstream = 'https://github.com/Remix-Design/RemixIcon'; revision = $Revision; icons = $rows }
[IO.File]::WriteAllText((Join-Path $root 'docs/remix-social-icons.json'), ($manifest | ConvertTo-Json -Depth 5) + "`n", $utf8)
Write-Output "Imported $($rows.Count) Remix Icon vectors at $Revision"
