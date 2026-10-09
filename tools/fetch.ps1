# Fast downloader: Invoke-WebRequest -OutFile throttles badly on large files,
# so stream the response through a fixed buffer instead.
param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][string]$OutFile
)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$dir = Split-Path -Parent $OutFile
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$partial = "$OutFile.part"

$request = [Net.HttpWebRequest]::Create($Url)
$request.Timeout = 60000
$request.ReadWriteTimeout = 300000
# Some mirrors reject requests without a User-Agent.
$request.UserAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) dsh-fetch/1.0'
$response = $request.GetResponse()
$total = $response.ContentLength
Write-Host ("downloading {0:N1} MB -> {1}" -f ($total / 1MB), $OutFile)

$sw = [Diagnostics.Stopwatch]::StartNew()
$input = $response.GetResponseStream()
$output = [IO.File]::Create($partial)
$buffer = New-Object byte[] (1MB)
$read = 0L
$lastReport = 0
try {
    while (($n = $input.Read($buffer, 0, $buffer.Length)) -gt 0) {
        $output.Write($buffer, 0, $n)
        $read += $n
        if ($sw.Elapsed.TotalSeconds - $lastReport -ge 10) {
            $lastReport = $sw.Elapsed.TotalSeconds
            Write-Host ("  {0:N0}% ({1:N1}/{2:N1} MB) {3:N1} MB/s" -f `
                (100 * $read / $total), ($read / 1MB), ($total / 1MB), ($read / 1MB / $sw.Elapsed.TotalSeconds))
        }
    }
} finally {
    $output.Close()
    $input.Close()
    $response.Close()
}
Move-Item $partial $OutFile -Force
Write-Host ("done: {0:N1} MB in {1:N0}s" -f ((Get-Item $OutFile).Length / 1MB), $sw.Elapsed.TotalSeconds)
