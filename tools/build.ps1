<#
  Builds a signed release APK without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.

  Usage:
    powershell -ExecutionPolicy Bypass -File tools\build.ps1
    powershell -ExecutionPolicy Bypass -File tools\build.ps1 -VersionName 1.0.1 -VersionCode 2
#>
[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkRoot  = $env:ANDROID_SDK_ROOT,
    [string]$VersionName = '1.0.0',
    [int]$VersionCode = 1,
    [switch]$SkipIcons
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ------------------------------------------------------------------ locate
$Root = Split-Path -Parent $PSScriptRoot

# aapt2 on Windows cannot open directories whose path contains non-ASCII
# characters, so build through an ASCII junction when the checkout path has any.
if ($Root -match '[^\x20-\x7E]') {
    # The junction has to live somewhere ASCII-writable. TEMP is the obvious choice but
    # is not guaranteed to be ASCII itself (a non-ASCII user name lands in it), so fall
    # back to the drive root.
    $link = $null
    foreach ($base in @($env:TEMP, $env:SystemDrive + '\')) {
        if (-not $base -or ($base -match '[^\x20-\x7E]')) { continue }
        $candidate = Join-Path $base 'coordcast-build'
        if (Test-Path (Split-Path -Parent $candidate)) { $link = $candidate; break }
    }
    if (-not $link) { throw 'No ASCII-writable directory found for the aapt2 junction; move the checkout to an ASCII path.' }
    if (-not (Test-Path $link)) {
        New-Item -ItemType Junction -Path $link -Target $Root -ErrorAction Stop | Out-Null
        Write-Host "created ASCII junction $link -> $Root"
    }
    $linkTarget = (Get-Item $link -Force).Target
    if ($linkTarget -and ($linkTarget -notcontains $Root)) {
        throw "$link already points at $linkTarget, expected $Root. Remove the junction and retry."
    }
    $Root = $link
}

if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium\jdk-17*'),
        (Join-Path $env:ProgramFiles 'Java\jdk-17*'),
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
        (Join-Path $env:ProgramFiles 'Microsoft\jdk-17*'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium\jdk-17*'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Microsoft\jdk-17*')
    )
    foreach ($c in $candidates) {
        $hit = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit -and (Test-Path (Join-Path $hit.FullName 'bin\javac.exe'))) { $JavaHome = $hit.FullName; break }
    }
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'JDK not found. Install a full JDK 17 and set JAVA_HOME.'
}
$JavaHome = (Resolve-Path $JavaHome).Path
# A full JDK ships lib/ct.sym; stripped runtimes reject -source/-target 8.
if (-not (Test-Path (Join-Path $JavaHome 'lib\ct.sym'))) {
    throw "$JavaHome is not a full JDK (lib/ct.sym missing); javac cannot target Java 8."
}

if (-not $SdkRoot -or -not (Test-Path $SdkRoot)) {
    foreach ($c in @($env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
        if ($c -and (Test-Path $c)) { $SdkRoot = $c; break }
    }
}
if (-not $SdkRoot -or -not (Test-Path $SdkRoot)) {
    throw 'Android SDK not found. Set ANDROID_SDK_ROOT.'
}
$SdkRoot = (Resolve-Path $SdkRoot).Path

$BuildTools = Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $BuildTools) { throw "no build-tools under $SdkRoot" }
$Platform = Get-ChildItem (Join-Path $SdkRoot 'platforms') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $Platform) { throw "no platform under $SdkRoot" }

$Aapt2     = Join-Path $BuildTools.FullName 'aapt2.exe'
$D8        = Join-Path $BuildTools.FullName 'd8.bat'
$ZipAlign  = Join-Path $BuildTools.FullName 'zipalign.exe'
$ApkSigner = Join-Path $BuildTools.FullName 'apksigner.bat'
$AndroidJar = Join-Path $Platform.FullName 'android.jar'
foreach ($tool in @($Aapt2, $D8, $ZipAlign, $ApkSigner, $AndroidJar)) {
    if (-not (Test-Path $tool)) { throw "missing tool: $tool" }
}

$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome 'bin') + ';' + $env:PATH

Write-Host "JDK        : $JavaHome"
Write-Host "SDK        : $SdkRoot"
Write-Host "build-tools: $($BuildTools.Name)"
Write-Host "platform   : $($Platform.Name)"
Write-Host ''

# ------------------------------------------------------------------- paths
$App       = Join-Path $Root 'app'
$Manifest  = Join-Path $App 'AndroidManifest.xml'
$ResDir    = Join-Path $App 'res'
$JavaDir   = Join-Path $App 'java'
$Build     = Join-Path $Root 'build'
$Dist      = Join-Path $Root 'dist'
$Gen       = Join-Path $Build 'gen'
$Classes   = Join-Path $Build 'classes'
$DexDir    = Join-Path $Build 'dex'
$ToolClasses = Join-Path $Build 'toolclasses'
$Keystore  = Join-Path $Root 'keystore\coordcast.jks'
$StorePass = 'coordcast2026'
$KeyAlias  = 'coordcast'
$MinSdk    = 24
$TargetSdk = 35

if (Test-Path $Build) { Remove-Item $Build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Build, $Dist, $Gen, $Classes, $DexDir, $ToolClasses | Out-Null

function Step($name) { Write-Host ''; Write-Host "=== $name" -ForegroundColor Cyan }
function Run($file, [string[]]$Arguments) {
    & $file @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$([IO.Path]::GetFileName($file)) failed with exit code $LASTEXITCODE" }
}

# ------------------------------------------------------------------- icons
if (-not $SkipIcons) {
    Step 'launcher bitmaps'
    Run (Join-Path $JavaHome 'bin\javac.exe') @(
        '-encoding', 'UTF-8', '-d', $ToolClasses, (Join-Path $PSScriptRoot 'src\IconGen.java'))
    Run (Join-Path $JavaHome 'bin\java.exe') @(
        '-Djava.awt.headless=true', '-cp', $ToolClasses, 'IconGen', $ResDir)
}

# --------------------------------------------------------------- resources
Step 'aapt2 compile'
$ResZip = Join-Path $Build 'res.zip'
Run $Aapt2 @('compile', '--dir', $ResDir, '-o', $ResZip)

Step 'aapt2 link'
$BaseApk = Join-Path $Build 'base.apk'
Run $Aapt2 @(
    'link',
    '-o', $BaseApk,
    '-I', $AndroidJar,
    '--manifest', $Manifest,
    '-R', $ResZip,
    '--java', $Gen,
    # Each configuration variant (values/, values-night/, values-v31/, ...) of the
    # same resource arrives as its own compiled unit, which aapt2 treats as a
    # redefinition unless overlays are allowed. Config variants still coexist.
    '--auto-add-overlay',
    '--min-sdk-version', "$MinSdk",
    '--target-sdk-version', "$TargetSdk",
    '--version-code', "$VersionCode",
    '--version-name', $VersionName
)

# ------------------------------------------------------------------ compile
Step 'javac'
# Modules live in sub-packages: castcore (pure Java), castmap, castui, app.
$Sources = @(Get-ChildItem (Join-Path $JavaDir 'com\coordcast') -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
$GeneratedR = Join-Path $Gen 'com\coordcast\R.java'
if (-not (Test-Path $GeneratedR)) {
    throw "R.java was not generated under $Gen"
}
$Sources += $GeneratedR
$Javac = Join-Path $JavaHome 'bin\javac.exe'
$JavacArgs = @(
    '-encoding', 'UTF-8',
    # android.jar goes on the CLASS path, not the boot class path: its
    # java.lang.invoke.LambdaMetafactory stub has no metafactory() method, which
    # javac needs when compiling lambdas. With --release 8 the java.* API comes from
    # the JDK's own Java 8 definitions and android.* from android.jar. d8 desugars
    # the lambdas afterwards.
    '--release', '8',
    '-classpath', $AndroidJar,
    '-Xlint:-options',
    # Deprecation notes come from deliberately guarded calls (setStatusBarColor,
    # setSystemUiVisibility) and only add noise to the log.
    '-nowarn',
    '-d', $Classes
) + $Sources
Run $Javac $JavacArgs

# ---------------------------------------------------------------------- dex
Step 'd8'
$ClassFiles = @(Get-ChildItem $Classes -Recurse -Filter *.class | ForEach-Object { $_.FullName })
if ($ClassFiles.Count -eq 0) { throw 'no .class files produced' }
$D8Args = @('--release', '--lib', $AndroidJar, '--min-api', "$MinSdk", '--output', $DexDir) + $ClassFiles
Run $D8 $D8Args
$DexFile = Join-Path $DexDir 'classes.dex'
if (-not (Test-Path $DexFile)) { throw "d8 produced no classes.dex in $DexDir" }

# --------------------------------------------------------------------- apk
Step 'package'
$ZipTool = Join-Path $ToolClasses 'ZipTool.class'
if (-not (Test-Path $ZipTool)) {
    Run (Join-Path $JavaHome 'bin\javac.exe') @(
        '-encoding', 'UTF-8', '-d', $ToolClasses, (Join-Path $PSScriptRoot 'src\ZipTool.java'))
}
$UnAligned = Join-Path $Build 'unsigned.apk'
Run (Join-Path $JavaHome 'bin\java.exe') @(
    '-cp', $ToolClasses, 'ZipTool', $UnAligned, $BaseApk, "classes.dex=$DexFile")

Step 'zipalign'
$Aligned = Join-Path $Build 'aligned.apk'
Run $ZipAlign @('-f', '-p', '4', $UnAligned, $Aligned)

# ---------------------------------------------------------------- keystore
if (-not (Test-Path $Keystore)) {
    Step 'generate signing key'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Keystore) | Out-Null
    Run (Join-Path $JavaHome 'bin\keytool.exe') @(
        '-genkeypair', '-v',
        '-keystore', $Keystore,
        '-storepass', $StorePass,
        '-keypass', $StorePass,
        '-alias', $KeyAlias,
        '-keyalg', 'RSA',
        '-keysize', '2048',
        '-validity', '10950',
        '-dname', 'CN=CoordCast, OU=Mobile, O=CoordCast, L=Shanghai, ST=Shanghai, C=CN'
    )
}

Step 'apksigner'
$ApkName = "CoordCast-$VersionName.apk"
$Apk = Join-Path $Dist $ApkName
if (Test-Path $Apk) { Remove-Item $Apk -Force }
Run $ApkSigner @(
    'sign',
    '--ks', $Keystore,
    '--ks-key-alias', $KeyAlias,
    '--ks-pass', "pass:$StorePass",
    '--key-pass', "pass:$StorePass",
    '--v1-signing-enabled', 'true',
    '--v2-signing-enabled', 'true',
    '--v3-signing-enabled', 'true',
    # v4 only produces a .idsig side file for incremental installs; skip the clutter.
    '--v4-signing-enabled', 'false',
    '--out', $Apk,
    $Aligned
)

# ------------------------------------------------------------------ verify
Step 'verify'
Run $ApkSigner @('verify', '--verbose', '--print-certs', $Apk)

# --------------------------------------------------------------- libraries
# The three reusable modules are packaged as ordinary jars, straight out of the same
# compile that produced the APK — so what a developer drops into their project is
# byte-for-byte the code that shipped in the app.
#
#   castcore   pure Java, no Android at all
#   castmap    Android: map app discovery, deep links, launching
#   castui     Android: the Material 3 Expressive component kit
#
# None of them carries resources (everything the castui kit draws is code), which is
# exactly why they can be plain jars rather than AARs.
Step 'libraries'
$Jar = Join-Path $JavaHome 'bin\jar.exe'
$Libs = @{}
foreach ($module in @('castcore', 'castmap', 'castui')) {
    $jarPath = Join-Path $Dist "$module-$VersionName.jar"
    if (Test-Path $jarPath) { Remove-Item $jarPath -Force }
    $pkgDir = Join-Path $Classes "com\coordcast\$module"
    if (-not (Test-Path $pkgDir)) { throw "no compiled classes for module $module" }
    # jar.exe stamps every entry with the class file's mtime, which makes two builds of
    # identical source differ. Repack through ZipTool, which writes one fixed date, so
    # the jars are as reproducible as the APK.
    $rawJar = Join-Path $Build "$module-raw.jar"
    if (Test-Path $rawJar) { Remove-Item $rawJar -Force }
    Run $Jar @('cf', $rawJar, '-C', $Classes, "com/coordcast/$module")
    Run (Join-Path $JavaHome 'bin\java.exe') @('-cp', $ToolClasses, 'ZipTool', $jarPath, $rawJar)
    Remove-Item $rawJar -Force
    $Libs[$module] = $jarPath
}

$hash = (Get-FileHash $Apk -Algorithm SHA256).Hash
$size = (Get-Item $Apk).Length
Write-Host ''
Write-Host 'BUILD OK' -ForegroundColor Green
Write-Host ("  apk    : {0}" -f $Apk)
Write-Host ("  size   : {0:N0} bytes ({1:N1} KB)" -f $size, ($size / 1KB))
Write-Host ("  sha256 : {0}" -f $hash)
foreach ($module in @('castcore', 'castmap', 'castui')) {
    $jarPath = $Libs[$module]
    $jarSize = (Get-Item $jarPath).Length
    Write-Host ("  {0,-9}: {1} ({2:N0} bytes)" -f $module, (Split-Path $jarPath -Leaf), $jarSize)
}

# ------------------------------------------------------------- sample check
# Compile the sample against the jars alone. If the public API is not actually usable
# from outside the project, this fails — which is the whole point of shipping jars.
Step 'sample'
$SampleOut = Join-Path $Build 'sample'
New-Item -ItemType Directory -Force -Path $SampleOut | Out-Null
$Sample = Join-Path $Root 'samples\Sample.java'
if (Test-Path $Sample) {
    Run (Join-Path $JavaHome 'bin\javac.exe') @(
        '-encoding', 'UTF-8',
        '-cp', "$($Libs['castcore']);$($Libs['castmap'])",
        '-d', $SampleOut, $Sample)
    Write-Host '  samples/Sample.java compiles against the jars'
}
exit 0
