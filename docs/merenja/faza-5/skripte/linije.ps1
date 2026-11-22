# Linije koda po modulu i udeo deljenog koda po klijentu (faza 5).
#
# Broje se samo Kotlin izvorni fajlovi (.kt); Gradle skripte, manifest i resursi ne.
# Linija koda je linija koja nije prazna i nije komentar (// ili /* ... */, ukljucujuci KDoc).
#
# Deljeni fajl (iz :core ili :app:shared) racuna se klijentu ako se neka njegova
# deklaracija najviseg nivoa, koja nije private, pojavljuje kao rec u kodu klijenta
# (bez komentara). Racunaju se i deljeni fajlovi koje taj fajl sam koristi, na isti nacin.
#
# Pokretanje iz bilo kog foldera:
#   powershell -ExecutionPolicy Bypass -File docs\merenja\faza-5\skripte\linije.ps1
# Rezultat: tabele na ekranu i moduli.csv, klijenti.csv pored izvestaja.

param([string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path)

$izlaz = Resolve-Path (Join-Path $PSScriptRoot "..")

# Kod fajla bez komentara i praznih linija.
function Read-KotlinCode([string]$path) {
    $kod = New-Object System.Collections.Generic.List[string]
    $uBloku = $false
    foreach ($l in Get-Content -LiteralPath $path -Encoding UTF8) {
        $t = $l.Trim()
        if ($uBloku) {
            if ($t -match '\*/') { $uBloku = $false }
            continue
        }
        if ($t -eq '' -or $t.StartsWith('//')) { continue }
        if ($t.StartsWith('/*')) {
            if ($t -notmatch '\*/') { $uBloku = $true }
            continue
        }
        $kod.Add($l)
    }
    return , $kod
}

function Get-SourceFiles([string]$dir) {
    $p = Join-Path $Root $dir
    if (-not (Test-Path $p)) { return @() }
    Get-ChildItem -LiteralPath $p -Recurse -Filter *.kt | ForEach-Object {
        $kod = Read-KotlinCode $_.FullName
        [pscustomobject]@{
            Fajl   = $_.FullName.Substring($Root.Length + 1).Replace('\', '/')
            Linija = @(Get-Content -LiteralPath $_.FullName -Encoding UTF8).Count
            Kod    = $kod.Count
            Tekst  = ($kod -join "`n")
        }
    }
}

# 1. Moduli -------------------------------------------------------------------------

$skupovi = [ordered]@{
    ":core (commonMain)"        = "core/src/commonMain"
    ":core (testovi)"           = "core/src/commonTest"
    ":app:shared (commonMain)"  = "app/shared/src/commonMain"
    ":app:shared (androidMain)" = "app/shared/src/androidMain"
    ":server"                   = "server/src/main"
    ":server (testovi)"         = "server/src/test"
    ":app:androidApp (telefon)" = "app/androidApp/src/main"
    ":app:tvApp (TV)"           = "app/tvApp/src/main"
}

$fajlovi = @{}
$moduli = foreach ($ime in $skupovi.Keys) {
    $f = @(Get-SourceFiles $skupovi[$ime])
    $fajlovi[$ime] = $f
    [pscustomobject]@{
        Modul  = $ime
        Fajlova = $f.Count
        Linija = ($f | Measure-Object Linija -Sum).Sum
        Kod    = ($f | Measure-Object Kod -Sum).Sum
    }
}

# 2. Deljeni fajlovi i njihove deklaracije ------------------------------------------

$deklaracija = '(?m)^(?:(?:public|internal|expect|actual|data|sealed|enum|abstract|open|inline|value|const)\s+)*(?:class|object|interface|fun|val|typealias)\s+([A-Za-z_]\w*)'
$deljeni = @($fajlovi[":core (commonMain)"]) + @($fajlovi[":app:shared (commonMain)"]) + @($fajlovi[":app:shared (androidMain)"])
foreach ($d in $deljeni) {
    $imena = [regex]::Matches($d.Tekst, $deklaracija) | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    $d | Add-Member -NotePropertyName Imena -NotePropertyValue @($imena)
}

function Test-Uses([string]$tekst, $fajl) {
    foreach ($ime in $fajl.Imena) {
        if ($tekst -match "\b$([regex]::Escape($ime))\b") { return $true }
    }
    return $false
}

# Deljeni fajlovi koje klijent koristi, sa zatvorenjem preko deljenih fajlova.
function Get-UsedShared([string]$tekst) {
    $korisceni = @{}
    $red = New-Object System.Collections.Queue
    foreach ($d in $deljeni) { if (Test-Uses $tekst $d) { $korisceni[$d.Fajl] = $d; $red.Enqueue($d) } }
    while ($red.Count -gt 0) {
        $tek = $red.Dequeue()
        foreach ($d in $deljeni) {
            if (-not $korisceni.ContainsKey($d.Fajl) -and (Test-Uses $tek.Tekst $d)) {
                $korisceni[$d.Fajl] = $d
                $red.Enqueue($d)
            }
        }
    }
    return @($korisceni.Values | Sort-Object Fajl)
}

# 3. Klijenti deljenog koda ----------------------------------------------------------

$klijenti = [ordered]@{ "Server" = ":server"; "Telefon" = ":app:androidApp (telefon)"; "TV" = ":app:tvApp (TV)" }
$udeo = foreach ($k in $klijenti.Keys) {
    $sopstveni = $fajlovi[$klijenti[$k]]
    $tekst = ($sopstveni | ForEach-Object Tekst) -join "`n"
    $korisceni = Get-UsedShared $tekst
    $sop = ($sopstveni | Measure-Object Kod -Sum).Sum
    $del = ($korisceni | Measure-Object Kod -Sum).Sum
    [pscustomobject]@{
        Klijent        = $k
        Sopstveni_kod  = $sop
        Deljeni_kod    = $del
        Udeo_deljenog  = [math]::Round(100 * $del / ($sop + $del), 1)
        Deljeni_fajlovi = ($korisceni | ForEach-Object { ($_.Fajl -split '/')[-1] }) -join ', '
    }
}

$niko = $deljeni | Where-Object {
    $f = $_
    -not ($udeo | Where-Object { $_.Deljeni_fajlovi -split ', ' -contains ($f.Fajl -split '/')[-1] })
}

$moduli | Format-Table -AutoSize
$udeo | Format-Table Klijent, Sopstveni_kod, Deljeni_kod, Udeo_deljenog -AutoSize
$udeo | ForEach-Object { "{0}: {1}" -f $_.Klijent, $_.Deljeni_fajlovi }
""
"Deljeni fajlovi koje ne koristi nijedan klijent: " + (($niko | ForEach-Object Fajl) -join ', ')
"Kod po deljenom fajlu:"
$deljeni | ForEach-Object { "  {0,4}  {1}" -f $_.Kod, $_.Fajl }

$moduli | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $izlaz "moduli.csv")
$udeo | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $izlaz "klijenti.csv")
