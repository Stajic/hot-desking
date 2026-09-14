$ErrorActionPreference = "Stop"
$bench = "C:\Users\Kol4n\AppData\Local\Temp\claude\C--Users-Kol4n-OneDrive-Desktop-DPL\86c4b99c-9d9b-4994-b992-2fece1b6f068\scratchpad\bench"
$jar   = "C:\Users\Kol4n\dev\hot-desking\server\build\libs\server-all.jar"
$java  = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe"
$jcmd  = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\jcmd.exe"
$k6    = "C:\Program Files\k6\k6.exe"
$work  = Join-Path $bench "loadrun"

New-Item -ItemType Directory -Force -Path $work | Out-Null

function Get-Mem {
    param($ProcId, $Faza)
    $p = Get-Process -Id $ProcId
    $rss = [math]::Round($p.WorkingSet64 / 1MB, 1)
    $priv = [math]::Round($p.PrivateMemorySize64 / 1MB, 1)
    $raw = & $jcmd $ProcId GC.heap_info 2>&1 | Out-String
    $used = $null; $committed = $null
    if ($raw -match 'used\s+(\d+)K') { $used = [math]::Round([int]$Matches[1] / 1024, 1) }
    if ($raw -match 'total\s+(\d+)K') { $committed = [math]::Round([int]$Matches[1] / 1024, 1) }
    [pscustomobject]@{ faza = $Faza; rss_mb = $rss; private_mb = $priv; heap_used_mb = $used; heap_total_mb = $committed }
}

Write-Host "--- podizanje servera ---"
$p = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) `
     -WorkingDirectory $work -PassThru -WindowStyle Hidden `
     -RedirectStandardOutput (Join-Path $bench "load-server.log") `
     -RedirectStandardError  (Join-Path $bench "load-server-err.log")

$deadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $deadline) {
    try { if ((Invoke-WebRequest "http://127.0.0.1:8080/health" -UseBasicParsing -TimeoutSec 1).StatusCode -eq 200) { break } } catch { Start-Sleep -Milliseconds 50 }
}
Write-Host "server PID $($p.Id) odgovara"

$resId = (Invoke-RestMethod "http://127.0.0.1:8080/api/resources" -TimeoutSec 10)[0].id
Write-Host "resurs za availability: $resId"

$mem = @()
Start-Sleep -Seconds 3
$mem += Get-Mem $p.Id "posle starta (mirovanje)"

$targets = @(
    @{ naziv = "resources";    putanja = "/api/resources" },
    @{ naziv = "availability"; putanja = "/api/resources/$resId/availability" }
)

# zagrevanje JVM-a - rezultat se odbacuje
Write-Host "--- zagrevanje (15s, 5 VU) ---"
$env:BASE = "http://127.0.0.1:8080"; $env:TARGET_PATH = "/api/resources"; $env:VUS = "5"; $env:DURATION = "15s"
$env:OUT = Join-Path $bench "warmup-summary.json"
& $k6 run --quiet (Join-Path $bench "load.js") | Out-Null

$mem += Get-Mem $p.Id "posle zagrevanja"

foreach ($t in $targets) {
    Write-Host "--- merenje: $($t.naziv)  (30s, 10 VU) ---"
    $env:TARGET_PATH = $t.putanja; $env:VUS = "10"; $env:DURATION = "30s"
    $env:OUT = Join-Path $bench "summary-$($t.naziv).json"
    & $k6 run --quiet (Join-Path $bench "load.js") | Out-Null
    $mem += Get-Mem $p.Id "pod opterecenjem: $($t.naziv)"
}

# posle prisilnog GC - koliko stvarno "drzi"
& $jcmd $p.Id GC.run 2>&1 | Out-Null
Start-Sleep -Seconds 2
$mem += Get-Mem $p.Id "posle GC.run"

$mem | Format-Table -AutoSize
$mem | Export-Csv (Join-Path $bench "memory.csv") -NoTypeInformation -Encoding utf8

Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
Write-Host "server zaustavljen"
