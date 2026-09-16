$ErrorActionPreference = "Stop"
$bench = "C:\Users\Kol4n\AppData\Local\Temp\claude\C--Users-Kol4n-OneDrive-Desktop-DPL\86c4b99c-9d9b-4994-b992-2fece1b6f068\scratchpad\bench"
$out   = Join-Path $bench "f2k1"
$jar   = "C:\Users\Kol4n\dev\hot-desking\server\build\libs\server-all.jar"
$jdk   = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin"
$java  = Join-Path $jdk "java.exe"
$jcmd  = Join-Path $jdk "jcmd.exe"
$k6    = "C:\Program Files\k6\k6.exe"
$base  = "http://127.0.0.1:8080"

New-Item -ItemType Directory -Force -Path $out | Out-Null
$work = Join-Path $out ("run-" + [guid]::NewGuid().ToString().Substring(0,8))
New-Item -ItemType Directory -Force -Path $work | Out-Null

function Get-Mem {
    param($ProcId, $Faza)
    $p = Get-Process -Id $ProcId
    $raw = & $jcmd $ProcId GC.heap_info 2>&1 | Out-String
    $used = $null; $total = $null
    if ($raw -match 'used\s+(\d+)K')  { $used  = [math]::Round([int]$Matches[1] / 1024, 1) }
    if ($raw -match 'total\s+(\d+)K') { $total = [math]::Round([int]$Matches[1] / 1024, 1) }
    [pscustomobject]@{
        faza = $Faza
        rss_mb = [math]::Round($p.WorkingSet64 / 1MB, 1)
        private_mb = [math]::Round($p.PrivateMemorySize64 / 1MB, 1)
        heap_used_mb = $used
        heap_total_mb = $total
    }
}

function Run-K6 {
    param($Naziv, $Metod, $Putanja, $Token, $Telo, $Vus, $Trajanje)
    $env:BASE = $base; $env:METHOD = $Metod; $env:TARGET_PATH = $Putanja
    $env:TOKEN = $Token; $env:BODY = $Telo; $env:EXPECT = "200"
    $env:VUS = "$Vus"; $env:DURATION = $Trajanje
    $env:OUT = Join-Path $out "summary-$Naziv.json"
    & $k6 run --quiet (Join-Path $bench "load2.js") | Out-Null
    Remove-Item Env:TOKEN, Env:BODY -ErrorAction SilentlyContinue
}

Write-Host "--- podizanje servera ---"
$p = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $work `
     -PassThru -WindowStyle Hidden `
     -RedirectStandardOutput (Join-Path $out "server.log") `
     -RedirectStandardError  (Join-Path $out "server-err.log")
$dl = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $dl) {
    try { if ((Invoke-WebRequest "$base/health" -UseBasicParsing -TimeoutSec 1).StatusCode -eq 200) { break } } catch { Start-Sleep -Milliseconds 50 }
}
Write-Host "server PID $($p.Id)"

$mem = @()
Start-Sleep -Seconds 3
$mem += Get-Mem $p.Id "posle starta (mirovanje)"

# priprema podataka: token i nekoliko rezervacija, da /mine ne vraca prazan niz
$loginBody = '{"email":"pera@firma.rs","password":"pera123"}'
$token = (Invoke-RestMethod "$base/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -TimeoutSec 20).token
$resId = (Invoke-RestMethod "$base/api/resources" -TimeoutSec 10)[0].id
$danas = [math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 86400000)
$napravljeno = 0
foreach ($d in 1..5) {
    $start = (($danas + $d) * 86400000) + (9 * 3600000) - (2 * 3600000)
    $b = @{ resourceId = $resId; startTime = $start; endTime = $start + 3600000 } | ConvertTo-Json
    try {
        Invoke-RestMethod "$base/api/bookings" -Method Post -Body $b -ContentType "application/json" `
            -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 20 | Out-Null
        $napravljeno++
    } catch { }
}
Write-Host "rezervacija za korisnika: $napravljeno"

Write-Host "--- zagrevanje (15s, 5 VU) ---"
Run-K6 -Naziv "warmup" -Metod "GET" -Putanja "/api/resources" -Token "" -Telo $null -Vus 5 -Trajanje "15s"
$mem += Get-Mem $p.Id "posle zagrevanja"

Write-Host "--- resources (javno) ---"
Run-K6 -Naziv "resources" -Metod "GET" -Putanja "/api/resources" -Token "" -Telo $null -Vus 10 -Trajanje "30s"
$mem += Get-Mem $p.Id "pod opterecenjem: resources"

Write-Host "--- availability (javno) ---"
Run-K6 -Naziv "availability" -Metod "GET" -Putanja "/api/resources/$resId/availability" -Token "" -Telo $null -Vus 10 -Trajanje "30s"
$mem += Get-Mem $p.Id "pod opterecenjem: availability"

Write-Host "--- mine (sa tokenom) ---"
Run-K6 -Naziv "mine" -Metod "GET" -Putanja "/api/bookings/mine" -Token $token -Telo $null -Vus 10 -Trajanje "30s"
$mem += Get-Mem $p.Id "pod opterecenjem: mine"

# prijava ide poslednja: BCrypt drzi procesor zauzetim i zagreva masinu
Write-Host "--- login (BCrypt) ---"
Run-K6 -Naziv "login" -Metod "POST" -Putanja "/api/auth/login" -Token "" -Telo $loginBody -Vus 10 -Trajanje "30s"
$mem += Get-Mem $p.Id "pod opterecenjem: login"

& $jcmd $p.Id GC.run 2>&1 | Out-Null
Start-Sleep -Seconds 2
$mem += Get-Mem $p.Id "posle GC.run"

$mem | Format-Table -AutoSize
$mem | Export-Csv (Join-Path $out "memory.csv") -NoTypeInformation -Encoding utf8

Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
Write-Host "server zaustavljen"
