# Restores the build toolchain (JDK 17 + Android build-tools/platform) into a local root.
#
#   powershell -ExecutionPolicy Bypass -File tools\restore-toolchain.ps1
#   powershell -ExecutionPolicy Bypass -File tools\restore-toolchain.ps1 -Root D:\android-build
#
# Downloads from the official mirrors; nothing here is project-specific.
[CmdletBinding()]
param(
    [string]$Root = $(if ($env:ANDROID_BUILD_ROOT) { $env:ANDROID_BUILD_ROOT } else { Join-Path $env:USERPROFILE 'android-build' })
)

$ErrorActionPreference = 'Stop'
$Root = [IO.Path]::GetFullPath($Root)
$dl = Join-Path $Root 'dl'
$sdk = Join-Path $Root 'sdk'
New-Item -ItemType Directory -Force -Path $dl | Out-Null

Write-Host "toolchain root: $Root"

$jobs = @(
    @{ name = 'jdk17.zip';      url = 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip' },
    @{ name = 'buildtools.zip'; url = 'https://dl.google.com/android/repository/build-tools_r35_windows.zip' },
    @{ name = 'platform35.zip'; url = 'https://dl.google.com/android/repository/platform-35_r02.zip' }
)
foreach ($j in $jobs) {
    $out = Join-Path $dl $j.name
    if (Test-Path $out) { Write-Host "have $($j.name)"; continue }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'fetch.ps1') -Url $j.url -OutFile $out
}

Write-Host '=== extracting ==='
$jdk = Join-Path $Root 'jdk17'
$bt  = Join-Path $sdk 'build-tools\35.0.0'
$plat = Join-Path $sdk 'platforms\android-35'

if (-not (Test-Path $jdk)) {
    $tmp = Join-Path $Root 'jdkx'
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    & tar -xf (Join-Path $dl 'jdk17.zip') -C $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    Move-Item $inner.FullName $jdk
    Remove-Item $tmp -Recurse -Force
}
if (-not (Test-Path $bt)) {
    $tmp = Join-Path $Root 'btx'
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    & tar -xf (Join-Path $dl 'buildtools.zip') -C $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    New-Item -ItemType Directory -Force -Path (Join-Path $sdk 'build-tools') | Out-Null
    Move-Item $inner.FullName $bt
    Remove-Item $tmp -Recurse -Force
}
if (-not (Test-Path $plat)) {
    New-Item -ItemType Directory -Force -Path (Join-Path $sdk 'platforms') | Out-Null
    & tar -xf (Join-Path $dl 'platform35.zip') -C (Join-Path $sdk 'platforms')
    if (-not (Test-Path $plat)) { throw 'platform extraction failed' }
}
Remove-Item $dl -Recurse -Force -ErrorAction SilentlyContinue

Write-Host '=== verify ==='
$expected = @(
    (Join-Path $jdk 'bin\javac.exe'),
    (Join-Path $jdk 'lib\ct.sym'),
    (Join-Path $bt 'aapt2.exe'),
    (Join-Path $bt 'd8.bat'),
    (Join-Path $bt 'apksigner.bat'),
    (Join-Path $plat 'android.jar')
)
foreach ($p in $expected) { Write-Host ("{0,-64} {1}" -f $p, (Test-Path $p)) }

Write-Host ''
Write-Host 'Now point the build at it:'
Write-Host "  `$env:JAVA_HOME = '$jdk'"
Write-Host "  `$env:ANDROID_SDK_ROOT = '$sdk'"
Get-PSDrive C | Select-Object @{n = 'FreeGB'; e = { [math]::Round($_.Free / 1GB, 2) } } | Format-List
