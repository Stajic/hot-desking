# Zajednicko za skripte faze 4: izbor baze i memorija procesa baze.
# Bez URL-a server radi nad H2 (podrazumevano); sa URL-om nad PostgreSQL-om preko
# promenljivih DB_*, isto kako bi se pokretao u stvarnoj upotrebi.

function Set-Db([string]$Url) {
    if ($Url) {
        $env:DB_DRIVER = "org.postgresql.Driver"
        $env:DB_URL    = $Url
        $env:DB_USER   = "postgres"
    } else {
        Remove-Item Env:DB_DRIVER, Env:DB_URL, Env:DB_USER, Env:DB_PASSWORD -ErrorAction SilentlyContinue
    }
}

# Zbir svih procesa postgres.exe (glavni proces, procesi po vezi, pozadinski procesi).
# RSS zbir precenjuje, jer se deljena memorija (shared_buffers) racuna u svakom
# procesu koji je koristi; privatna memorija je pouzdaniji zbir.
function Get-PgMem {
    $ps = @(Get-Process postgres -ErrorAction SilentlyContinue)
    [pscustomobject]@{
        pg_procesa    = $ps.Count
        pg_rss_mb     = [math]::Round((($ps | Measure-Object WorkingSet64 -Sum).Sum) / 1MB, 1)
        pg_private_mb = [math]::Round((($ps | Measure-Object PrivateMemorySize64 -Sum).Sum) / 1MB, 1)
    }
}
