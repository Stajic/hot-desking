$ErrorActionPreference = "Stop"
$bench = "C:\Users\Kol4n\AppData\Local\Temp\claude\C--Users-Kol4n-OneDrive-Desktop-DPL\86c4b99c-9d9b-4994-b992-2fece1b6f068\scratchpad\bench"
$out   = Join-Path $bench "f2kraj\repro"
$jar   = "C:\Users\Kol4n\dev\hot-desking\server\build\libs\server-all.jar"
$java  = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe"
$k6    = "C:\Program Files\k6\k6.exe"
$base  = "http://127.0.0.1:8080"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$work = Join-Path $out ("run-" + [guid]::NewGuid().ToString().Substring(0,8))
New-Item -ItemType Directory -Force -Path $work | Out-Null

$p = Start-Process -FilePath $java -ArgumentList @('-Xlog:gc:file=' + (Join-Path $out 'gc.log'), '-jar', $jar) `
     -WorkingDirectory $work -PassThru -WindowStyle Hidden `
     -RedirectStandardOutput (Join-Path $out "server.log") -RedirectStandardError (Join-Path $out "server-err.log")
$dl = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $dl) { try { if ((Invoke-WebRequest "$base/health" -UseBasicParsing -TimeoutSec 1).StatusCode -eq 200) { break } } catch { Start-Sleep -Milliseconds 50 } }

# ista priprema kao u load3.ps1: prijava i 5 rezervacija, pa zagrevanje samo na /api/resources
$loginBody = '{"email":"pera@firma.rs","password":"pera123"}'
$token = (Invoke-RestMethod "$base/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -TimeoutSec 20).token
$resId = (Invoke-RestMethod "$base/api/resources" -TimeoutSec 10)[0].id
$danas = [math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 86400000)
foreach ($d in 1..5) {
    $start = (($danas + $d) * 86400000) + (9 * 3600000) - (2 * 3600000)
    $b = @{ resourceId = $resId; startTime = $start; endTime = $start + 3600000 } | ConvertTo-Json
    try { Invoke-RestMethod "$base/api/bookings" -Method Post -Body $b -ContentType "application/json" -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 20 | Out-Null } catch { }
}
$env:BASE = $base; $env:METHOD = "GET"; $env:TARGET_PATH = "/api/resources"; $env:EXPECT = "200"; $env:VUS = "5"; $env:DURATION = "15s"
$env:OUT = Join-Path $out "warmup.json"
& $k6 run --quiet (Join-Path $bench "load2.js") | Out-Null

# 1) PRVI zahtev na availability - pojedinacno izmeren
$t = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-WebRequest "$base/api/resources/$resId/availability" -UseBasicParsing -TimeoutSec 30 | Out-Null
$t.Stop()
Write-Host ("prvi zahtev na /availability: {0:N1} ms" -f $t.Elapsed.TotalMilliseconds)

# 2) dva uzastopna prolaza pod opterecenjem
foreach ($n in 1..2) {
    $env:TARGET_PATH = "/api/resources/$resId/availability"; $env:VUS = "10"; $env:DURATION = "30s"
    $env:OUT = Join-Path $out "availability-$n.json"
    & $k6 run --quiet (Join-Path $bench "load2.js") | Out-Null
    $v = (Get-Content $env:OUT -Raw | ConvertFrom-Json).metrics.http_req_duration.values
    Write-Host ("prolaz {0}: med {1:N2} ms | p99 {2:N2} ms | max {3:N2} ms" -f $n, $v.med, $v.'p(99)', $v.max)
}

Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue

# najduze GC pauze
$gc = Get-Content (Join-Path $out "gc.log") -ErrorAction SilentlyContinue | Select-String -Pattern "Pause.*?(\d+\.\d+)ms" -AllMatches
$max = ($gc | ForEach-Object { [double]$_.Matches[0].Groups[1].Value } | Measure-Object -Maximum).Maximum
Write-Host ("GC pauza: {0} zabelezenih, najduza {1:N2} ms" -f $gc.Count, $max)
