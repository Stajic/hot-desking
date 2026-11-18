# Vreme podizanja: 3 hladna starta (prazna baza - sema i pocetni podaci) i 5 toplih
# (baza vec postoji). Isto kao startup4.ps1 iz faze 3, uz izbor baze:
#  - bez -DbUrls: H2, svaki hladni start u novom folderu;
#  - sa -DbUrls (4 adrese): PostgreSQL, hladni startovi nad prve tri prazne baze,
#    topli nad cetvrtom.
param([Parameter(Mandatory)] [string]$Jar, [Parameter(Mandatory)] [string]$Out, [string[]]$DbUrls = @())
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "db5.ps1")
$java  = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe"
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$bench = Join-Path $Out ("startup-" + [guid]::NewGuid().ToString().Substring(0,8))
New-Item -ItemType Directory -Force -Path $bench | Out-Null
if ($DbUrls.Count -ne 0 -and $DbUrls.Count -ne 4) { throw "DbUrls: ocekivane 4 adrese (3 hladna + 1 topla)" }

function Measure-Start {
    param($Label, $WorkDir, $Idx, $DbUrl)
    New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
    $log = Join-Path $bench "log-$Label-$Idx.txt"
    $err = Join-Path $bench "err-$Label-$Idx.txt"
    Set-Db $DbUrl

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $p = Start-Process -FilePath $java -ArgumentList @('-jar', $Jar) `
         -WorkingDirectory $WorkDir -PassThru -WindowStyle Hidden `
         -RedirectStandardOutput $log -RedirectStandardError $err

    $ok = $false
    while ($sw.Elapsed.TotalSeconds -lt 60) {
        try {
            $r = Invoke-WebRequest "http://127.0.0.1:8080/health" -UseBasicParsing -TimeoutSec 1
            if ($r.StatusCode -eq 200) { $ok = $true; break }
        } catch { Start-Sleep -Milliseconds 15 }
    }
    $sw.Stop()
    $wall = [math]::Round($sw.Elapsed.TotalMilliseconds)

    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 700
    Set-Db $null

    $ktorMs = $null
    $m = Select-String -Path $log -Pattern "Application started in ([\d.]+) seconds" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($m) { $ktorMs = [math]::Round([double]$m.Matches.Groups[1].Value * 1000) }

    [pscustomobject]@{ tip = $Label; broj = $Idx; ukupno_ms = $wall; ktor_ms = $ktorMs; ok = $ok }
}

$pg = $DbUrls.Count -eq 4
$res = @()
foreach ($i in 1..3) {
    $res += Measure-Start -Label "hladan" -WorkDir (Join-Path $bench "cold$i") -Idx $i -DbUrl $(if ($pg) { $DbUrls[$i - 1] } else { $null })
}
$warm = Join-Path $bench "warm"
$warmUrl = if ($pg) { $DbUrls[3] } else { $null }
$null = Measure-Start -Label "zagrevanje" -WorkDir $warm -Idx 0 -DbUrl $warmUrl
foreach ($i in 1..5) { $res += Measure-Start -Label "topao" -WorkDir $warm -Idx $i -DbUrl $warmUrl }

function Median($arr) {
    $s = @($arr | Where-Object { $_ -ne $null } | Sort-Object)
    if ($s.Count -eq 0) { return $null }
    return $s[[math]::Floor($s.Count / 2)]
}
foreach ($t in @("hladan", "topao")) {
    $g = $res | Where-Object { $_.tip -eq $t }
    "{0,-8} ukupno: {1,5} ms   Ktor: {2,5} ms   (n={3}, ok={4})" -f $t, (Median $g.ukupno_ms), (Median $g.ktor_ms), $g.Count, (@($g | Where-Object ok).Count)
}
$res | Export-Csv (Join-Path $Out "startup.csv") -NoTypeInformation -Encoding utf8
