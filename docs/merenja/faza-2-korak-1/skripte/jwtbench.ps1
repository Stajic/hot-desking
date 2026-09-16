$bench  = "C:\Users\Kol4n\AppData\Local\Temp\claude\C--Users-Kol4n-OneDrive-Desktop-DPL\86c4b99c-9d9b-4994-b992-2fece1b6f068\scratchpad\bench"
$jar    = "C:\Users\Kol4n\dev\hot-desking\server\build\libs\server-all.jar"
$jshell = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\jshell.exe"
$o = & $jshell --class-path $jar -q (Join-Path $bench "jwtbench.jsh") 2>&1 | Out-String
$lines = ($o -split "`n") | Where-Object { $_ -match "VERIFY_NS|SIGN_NS" }
$lines
$lines | Set-Content (Join-Path $bench "f2k1\jwt-micro.txt") -Encoding utf8
