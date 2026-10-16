# ============================================================
#  CSA Java gateway one-click start (local ops tooling).
#  DB credentials are read from config\db.local.json when
#  present; otherwise the defaults below are used.
#  CHANGE the default password if your local DB differs.
# ============================================================
[CmdletBinding()]
param(
    [int]$Port = 1234
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

# --- JAVA_HOME: prefer E:\tools\jdk-21.0.12.1+1 when present ---
$JdkHome = 'E:\tools\jdk-21.0.12.1+1'
if (Test-Path (Join-Path $JdkHome 'bin\java.exe')) {
    $env:JAVA_HOME = $JdkHome
}

if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $JavaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $JavaExe = 'java'
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Write-Host "[ERROR] java not found. Install JDK 21 at $JdkHome or set JAVA_HOME." -ForegroundColor Red
        exit 1
    }
}

# --- CSA_DB_* defaults (change password here if not using config\db.local.json) ---
$env:CSA_DB_HOST = '192.168.2.100'
$env:CSA_DB_PORT = '3306'
$env:CSA_DB_USER = 'csa'
$env:CSA_DB_PASS = '0OKM)okm123'   # CHANGE ME for local ops if needed
$env:CSA_DB_NAME = 'csa'

# --- Prefer config\db.local.json when present ---
$cfgPath = Join-Path $Root 'config\db.local.json'
if (Test-Path $cfgPath) {
    Write-Host "[INFO] Loading DB settings from $cfgPath"
    $cfg = Get-Content -Raw -Encoding UTF8 -LiteralPath $cfgPath | ConvertFrom-Json
    if ($cfg.host) { $env:CSA_DB_HOST = [string]$cfg.host }
    if ($cfg.port) { $env:CSA_DB_PORT = [string]$cfg.port }
    if ($cfg.user) { $env:CSA_DB_USER = [string]$cfg.user }
    if ($null -ne $cfg.password -and $cfg.password -ne '') { $env:CSA_DB_PASS = [string]$cfg.password }
    if ($cfg.database) { $env:CSA_DB_NAME = [string]$cfg.database }
}

$cp = (Join-Path $Root 'java\out') + ';E:\tools\jars\mysql-connector-j.jar'

Write-Host "[INFO] JAVA_HOME=$env:JAVA_HOME"
Write-Host "[INFO] DB=$($env:CSA_DB_USER)@$($env:CSA_DB_HOST):$($env:CSA_DB_PORT)/$($env:CSA_DB_NAME)"
Write-Host "[INFO] classpath=$cp"
Write-Host "[INFO] Starting csa.client.GatewayMain on port $Port ..."

& $JavaExe -cp $cp csa.client.GatewayMain $Port
exit $LASTEXITCODE
