# Rute koje je faza 3 uvela ili pocela da koristi, na svezem serveru:
#  - GET /api/resources?amenity=projektor (podupit iz koraka 1), uz /api/resources
#    u istom pokretanju radi direktnog poredjenja;
#  - GET /api/bookings (globalni pregled, bez stranicenja) pri 100, 1 000 i 10 000
#    rezervacija - jedan administrator (1 VU) i opterecenje (10 VU).
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

function Get-HeapAfterGc($ProcId) {
    & $jcmd $ProcId GC.run 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    $raw = & $jcmd $ProcId GC.heap_info 2>&1 | Out-String
    if ($raw -match 'used\s+(\d+)K') { return [math]::Round([int]$Matches[1] / 1024, 1) }
}

function Run-K6 {
    param($Naziv, $Putanja, $Token, $Vus, $Trajanje)
    $env:BASE = $base; $env:METHOD = "GET"; $env:TARGET_PATH = $Putanja
    $env:TOKEN = $Token; $env:EXPECT = "200"
    $env:VUS = "$Vus"; $env:DURATION = $Trajanje
    $env:OUT = Join-Path $Out "summary-$Naziv.json"
    & $k6 run --quiet (Join-Path $bench "load2.js") | Out-Null
    Remove-Item Env:TOKEN -ErrorAction SilentlyContinue
    $v = (Get-Content $env:OUT -Raw | ConvertFrom-Json).metrics.http_req_duration.values
    $n = (Get-Content $env:OUT -Raw | ConvertFrom-Json).metrics.http_reqs.values
    "{0,-28} zahteva/s {1,8:N1} | med {2,8:N2} ms | p99 {3,8:N2} ms | max {4,8:N2} ms" -f $Naziv, $n.rate, $v.med, $v.'p(99)', $v.max
}

$p = Start-Process -FilePath $java -ArgumentList @('-jar', $Jar) -WorkingDirectory $work `
     -PassThru -WindowStyle Hidden `
     -RedirectStandardOutput (Join-Path $Out "server.log") `
     -RedirectStandardError  (Join-Path $Out "server-err.log")
$dl = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $dl) {
    try { if ((Invoke-WebRequest "$base/health" -UseBasicParsing -TimeoutSec 1).StatusCode -eq 200) { break } } catch { Start-Sleep -Milliseconds 50 }
}

function Login($e, $pw) {
    (Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ email = $e; password = $pw } | ConvertTo-Json) -TimeoutSec 20).token
}
$admin = Login "admin@firma.rs" "admin123"
$pera  = Login "pera@firma.rs" "pera123"
$resIds = ((Invoke-RestMethod "$base/api/resources" -TimeoutSec 10) | ForEach-Object { $_.id }) -join ","

Run-K6 -Naziv "warmup" -Putanja "/api/resources" -Token "" -Vus 5 -Trajanje "15s" | Out-Null

# --- filter po opremi, uz osnovnu listu u istom pokretanju ---
Run-K6 -Naziv "resources" -Putanja "/api/resources" -Token "" -Vus 10 -Trajanje "30s"
Run-K6 -Naziv "resources-amenity" -Putanja "/api/resources?amenity=projektor" -Token "" -Vus 10 -Trajanje "30s"

# --- globalni pregled pri rastucem broju rezervacija ---
$day0 = [DateTimeOffset]::new((Get-Date).Date.AddDays(1).AddHours(8)).ToUnixTimeMilliseconds()
$ima = 0
$rez = @()
foreach ($cilj in 100, 1000, 10000) {
    $env:BASE = $base; $env:TOKENS = "$pera,$admin"; $env:RES_IDS = $resIds; $env:DAY0 = "$day0"
    $env:OFFSET = "$ima"; $env:COUNT = "$($cilj - $ima)"; $env:OUT = Join-Path $Out "seed-$cilj.json"
    & $k6 run --quiet (Join-Path $bench "seed-bookings.js") | Out-Null
    $ok = (Get-Content $env:OUT -Raw | ConvertFrom-Json).metrics.checks.values.passes
    $ima = $cilj

    $r = Invoke-WebRequest "$base/api/bookings" -Headers @{ Authorization = "Bearer $admin" } -UseBasicParsing -TimeoutSec 60
    $broj = ($r.Content | ConvertFrom-Json).Count
    $kb = [math]::Round($r.RawContentLength / 1024, 1)
    $heap = Get-HeapAfterGc $p.Id
    "--- $cilj rezervacija: upisano u ovom koraku $ok, u odgovoru $broj, odgovor $kb KB, gomila posle GC $heap MB"

    $a = Run-K6 -Naziv "bookings-$cilj-1vu" -Putanja "/api/bookings" -Token $admin -Vus 1 -Trajanje "15s"
    $b = Run-K6 -Naziv "bookings-$cilj-10vu" -Putanja "/api/bookings" -Token $admin -Vus 10 -Trajanje "30s"
    $a; $b
    $rez += [pscustomobject]@{ rezervacija = $cilj; u_odgovoru = $broj; odgovor_kb = $kb; gomila_posle_gc_mb = $heap }
}
Remove-Item Env:TOKENS, Env:RES_IDS, Env:DAY0, Env:OFFSET, Env:COUNT -ErrorAction SilentlyContinue

$rez | Export-Csv (Join-Path $Out "global.csv") -NoTypeInformation -Encoding utf8
Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
