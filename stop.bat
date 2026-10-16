@echo off
setlocal EnableExtensions
rem ============================================================
rem  Stop the Java gateway (csa.client.GatewayMain).
rem ============================================================

echo [INFO] Looking for java processes running GatewayMain ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ps = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*GatewayMain*' }; if (-not $ps) { Write-Host '[INFO] No GatewayMain java process found.'; exit 0 }; foreach ($p in $ps) { Write-Host ('[INFO] Killing PID ' + $p.ProcessId); taskkill /PID $p.ProcessId /F }"
exit /b 0
