# Compiles and runs the desktop unit tests for the pure-Java core (no Android needed).
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Root = Split-Path -Parent $PSScriptRoot

# Same JDK discovery as tools/build.ps1: JAVA_HOME first, then the usual install
# locations. No machine-specific paths — this script ships in a public repo.
$JavaHome = $env:JAVA_HOME
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
$Javac = Join-Path $JavaHome 'bin\javac.exe'
$Java  = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path $Javac)) { throw "javac not found at $Javac (set JAVA_HOME to a full JDK 17)" }

$Out = Join-Path $Root 'build\coretest'
Remove-Item $Out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Out | Out-Null

# castcore is the pure-Java module and is compiled whole. From castmap only the
# table/URI classes are platform independent — the Intent- and PackageManager-facing
# ones, the glass views and the demo activity all need android.jar.
$Core = @()
$Core += Get-ChildItem (Join-Path $Root 'app\java\com\coordcast\castcore\*.java') |
    ForEach-Object { $_.FullName }
$Core += @('MapApp.java', 'MapLinks.java', 'AmapUris.java') |
    ForEach-Object { Join-Path $Root "app\java\com\coordcast\castmap\$_" }

$Sources = @($Core) + @((Join-Path $Root 'tools\src\CoreTests.java'))
Write-Host "compiling $($Sources.Count) files -> $Out"
& $Javac -encoding UTF-8 -d $Out @Sources
if ($LASTEXITCODE -ne 0) { throw 'core compile failed' }

& $Java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $Out CoreTests
exit $LASTEXITCODE
