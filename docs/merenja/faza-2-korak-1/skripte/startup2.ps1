$ErrorActionPreference = "Stop"
# Razlika u odnosu na startup.ps1 iz faze 1: sve ide u jedinstven, nov folder.
# Folderi cold1..3 i warm iz faze 1 i dalje postoje sa bazama, pa bi "hladni"
# startovi nad njima zapravo bili topli.
$root  = "C:\Users\Kol4n\AppData\Local\Temp\claude\C--Users-Kol4n-OneDrive-Desktop-DPL\86c4b99c-9d9b-4994-b992-2fece1b6f068\scratchpad\bench\f2k1"
$bench = Join-Path $root ("startup-" + [guid]::NewGuid().ToString().Substring(0,8))
New-Item -ItemType Directory -Force -Path $bench | Out-Null
$jar   = "C:\Users\Kol4n\dev\hot-desking\server\build\libs\server-all.jar"
$java  = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe"

function Measure-Start {
    param($Label, $WorkDir, $Idx)
    New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
    $log = Join-Path $bench "log-$Label-$Idx.txt"
    $err = Join-Path $bench "err-$Label-$Idx.txt"

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $p = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) `
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

    $ktorMs = $null
    $m = Select-String -Path $log -Pattern "Application started in ([\d.]+) seconds" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($m) { $ktorMs = [math]::Round([double]$m.Matches.Groups[1].Value * 1000) }

    [pscustomobject]@{ tip = $Label; broj = $Idx; ukupno_ms = $wall; ktor_ms = $ktorMs; ok = $ok }
}

$res = @()

foreach ($i in 1..3) {
    $res += Measure-Start -Label "hladan" -WorkDir (Join-Path $bench "cold$i") -Idx $i
}

$warm = Join-Path $bench "warm"
$null = Measure-Start -Label "zagrevanje" -WorkDir $warm -Idx 0
foreach ($i in 1..5) {
    $res += Measure-Start -Label "topao" -WorkDir $warm -Idx $i
}

$res | Format-Table -AutoSize

function Median($arr) {
    $s = @($arr | Where-Object { $_ -ne $null } | Sort-Object)
    if ($s.Count -eq 0) { return $null }
    return $s[[math]::Floor($s.Count / 2)]
}

"--- medijane ---"
foreach ($t in @("hladan", "topao")) {
    $g = $res | Where-Object { $_.tip -eq $t }
    "{0,-8} ukupno: {1,5} ms   Ktor: {2,5} ms   (n={3})" -f $t, (Median $g.ukupno_ms), (Median $g.ktor_ms), $g.Count
}

$res | Export-Csv (Join-Path $root "startup.csv") -NoTypeInformation -Encoding utf8
"zapisano: f2k1\startup.csv"
