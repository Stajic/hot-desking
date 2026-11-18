# Iz k6 izvestaja (summary-*.json) pravi latency.csv u istom obliku kao u fazi 2.
# k6 drzi brojeve pod metrics.<ime>.values.
param([Parameter(Mandatory)] [string]$Dir, [string[]]$Rute = @("resources", "availability", "mine", "login"))
$rows = foreach ($r in $Rute) {
    $f = Join-Path $Dir "summary-$r.json"
    if (-not (Test-Path $f)) { continue }
    $j = Get-Content $f -Raw | ConvertFrom-Json
    $d = $j.metrics.http_req_duration.values
    $n = $j.metrics.http_reqs.values
    $fail = $j.metrics.http_req_failed.values
    $chk = $j.metrics.checks.values
    [pscustomobject]@{
        ruta = $r
        zahteva = $n.count
        'zahteva/s' = [math]::Round($n.rate, 1)
        'pad %' = [math]::Round($fail.rate * 100, 2)
        min = [math]::Round($d.min, 2); med = [math]::Round($d.med, 2); avg = [math]::Round($d.avg, 2)
        p90 = [math]::Round($d.'p(90)', 2); p95 = [math]::Round($d.'p(95)', 2)
        p99 = [math]::Round($d.'p(99)', 2); max = [math]::Round($d.max, 2)
        greske = $chk.fails
    }
}
$rows | Export-Csv (Join-Path $Dir "latency.csv") -NoTypeInformation -Encoding utf8
$rows
