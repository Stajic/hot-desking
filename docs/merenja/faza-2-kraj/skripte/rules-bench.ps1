$bench  = "C:\Users\Kol4n\AppData\Local\Temp\claude\C--Users-Kol4n-OneDrive-Desktop-DPL\86c4b99c-9d9b-4994-b992-2fece1b6f068\scratchpad\bench"
$jshell = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\jshell.exe"
$stari  = "C:\Users\Kol4n\AppData\Local\Temp\claude\C--Users-Kol4n-OneDrive-Desktop-DPL\86c4b99c-9d9b-4994-b992-2fece1b6f068\scratchpad\hd-stari\server\build\libs\server-all.jar"
$novi   = "C:\Users\Kol4n\dev\hot-desking\server\build\libs\server-all.jar"

$rez = @()
foreach ($v in @(@{ ime = "stari (fiksni +2)"; jar = $stari; jsh = "rules-stari.jsh" },
                 @{ ime = "novi (kotlinx-datetime)"; jar = $novi; jsh = "rules-novi.jsh" })) {
    $o = & $jshell --class-path $v.jar -q (Join-Path $bench $v.jsh) 2>&1 | Out-String
    $val = [regex]::Match($o, "VALIDATE_NS=.*MED=([\d.E-]+)").Groups[1].Value
    $day = [regex]::Match($o, "DAYSTART_NS=.*MED=([\d.E-]+)").Groups[1].Value
    if (-not $val) { Write-Host "GRESKA za $($v.ime):"; Write-Host $o; continue }
    Write-Host "$($v.ime)"
    ($o -split "`n") | Where-Object { $_ -match "_NS=" } | ForEach-Object { Write-Host "  $($_.Trim())" }
    $rez += [pscustomobject]@{ verzija = $v.ime; validate_ns = [math]::Round([double]$val, 2); dayStart_ns = [math]::Round([double]$day, 2) }
}
$rez | Format-Table -AutoSize
$rez | Export-Csv (Join-Path $bench "f2kraj\rules-micro.csv") -NoTypeInformation -Encoding utf8
