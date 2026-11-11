# Odziv, propusnost i memorija na istim rutama i istim redom kao load3.ps1 iz faze 2:
# zagrevanje, resources, availability, mine, login. JAR i izlazni folder su parametri,
# da bi se ista skripta pustila nad obe verzije.
param([Parameter(Mandatory)] [string]$Jar, [Parameter(Mandatory)] [string]$Out)
$ErrorActionPreference = "Stop"
$bench = $PSScriptRoot
$jdk   = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin"
$java  = Join-Path $jdk "java.exe"
$jcmd  = Join-Path $jdk "jcmd.exe"
$k6    = "C:\Program Files\k6\k6.exe"
$base  = "http://127.0.0.1:8080"

New-Item -ItemType Directory -Force -Path $Out | Out-Null
$work = Join-Path $Out ("run-" + [guid]::NewGuid().ToString().Substring(0,8))
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
    $env:OUT = Join-Path $Out "summary-$Naziv.json"
    & $k6 run --quiet (Join-Path $bench "load2.js") | Out-Null
    Remove-Item Env:TOKEN, Env:BODY -ErrorAction SilentlyContinue
}

$p = Start-Process -FilePath $java -ArgumentList @('-jar', $Jar) -WorkingDirectory $work `
     -PassThru -WindowStyle Hidden `
     -RedirectStandardOutput (Join-Path $Out "server.log") `
     -RedirectStandardError  (Join-Path $Out "server-err.log")
$dl = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $dl) {
    try { if ((Invoke-WebRequest "$base/health" -UseBasicParsing -TimeoutSec 1).StatusCode -eq 200) { break } } catch { Start-Sleep -Milliseconds 50 }
}

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
"rezervacija pripremljeno: $napravljeno"

Run-K6 -Naziv "warmup" -Metod "GET" -Putanja "/api/resources" -Token "" -Telo $null -Vus 5 -Trajanje "15s"
$mem += Get-Mem $p.Id "posle zagrevanja"
Run-K6 -Naziv "resources" -Metod "GET" -Putanja "/api/resources" -Token "" -Telo $null -Vus 10 -Trajanje "30s"
$mem += Get-Mem $p.Id "pod opterecenjem: resources"
Run-K6 -Naziv "availability" -Metod "GET" -Putanja "/api/resources/$resId/availability" -Token "" -Telo $null -Vus 10 -Trajanje "30s"
$mem += Get-Mem $p.Id "pod opterecenjem: availability"
Run-K6 -Naziv "mine" -Metod "GET" -Putanja "/api/bookings/mine" -Token $token -Telo $null -Vus 10 -Trajanje "30s"
$mem += Get-Mem $p.Id "pod opterecenjem: mine"
# prijava ide poslednja: BCrypt drzi procesor zauzetim i zagreva masinu
Run-K6 -Naziv "login" -Metod "POST" -Putanja "/api/auth/login" -Token "" -Telo $loginBody -Vus 10 -Trajanje "30s"
$mem += Get-Mem $p.Id "pod opterecenjem: login"

& $jcmd $p.Id GC.run 2>&1 | Out-Null
Start-Sleep -Seconds 2
$mem += Get-Mem $p.Id "posle GC.run"

$mem | Format-Table -AutoSize
$mem | Export-Csv (Join-Path $Out "memory.csv") -NoTypeInformation -Encoding utf8
Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
