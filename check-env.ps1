# ============================================================
#  CSA Java gateway environment check (local ops tooling).
#  Verifies: java, mysql-connector jar, config\db.local.json,
#  listen port free, DB host reachability.
# ============================================================
[CmdletBinding()]
param(
    [int]$Port = 1234
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$fail = 0

function Ok([string]$msg)  { Write-Host "[ OK ] $msg" -ForegroundColor Green }
function Warn([string]$msg){ Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Bad([string]$msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; $script:fail++ }

Write-Host "=== CSA gateway check-env ==="
Write-Host "Root: $Root"
Write-Host ""

# --- 1. Java / JDK ---
$JdkHome = 'E:\tools\jdk-21.0.12.1+1'
if (Test-Path (Join-Path $JdkHome 'bin\java.exe')) {
    $env:JAVA_HOME = $JdkHome
    Ok "JDK present at $JdkHome"
} else {
    Warn "JDK not at $JdkHome (will fall back to JAVA_HOME / PATH)"
}

if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $JavaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) {
        $JavaExe = $cmd.Source
    } else {
        $JavaExe = $null
    }
}

if ($JavaExe) {
    try {
        $ver = & $JavaExe -version 2>&1 | Select-Object -First 1
        Ok "java: $JavaExe"
        Write-Host "       $ver"
    } catch {
        Bad "java failed to run: $JavaExe"
    }
} else {
    Bad "java not found (install JDK 21 at $JdkHome or set JAVA_HOME)"
}

# --- 2. MySQL connector jar ---
$jar = 'E:\tools\jars\mysql-connector-j.jar'
if (Test-Path $jar) {
    Ok "mysql-connector-j.jar present: $jar"
} else {
    Bad "mysql-connector-j.jar missing: $jar"
}

# --- 3. Compiled gateway classes ---
$outMain = Join-Path $Root 'java\out\csa\client\GatewayMain.class'
if (Test-Path $outMain) {
    Ok "compiled GatewayMain.class present"
} else {
    Warn "GatewayMain.class missing under java\out (run javac first)"
}

# --- 4. config\db.local.json ---
$cfgPath = Join-Path $Root 'config\db.local.json'
$dbHost = '192.168.2.100'
$dbPort = 3306
$dbUser = 'csa'
$dbName = 'csa'
if (Test-Path $cfgPath) {
    try {
        $cfg = Get-Content -Raw -Encoding UTF8 -LiteralPath $cfgPath | ConvertFrom-Json
        if ($cfg.host)     { $dbHost = [string]$cfg.host }
        if ($cfg.port)     { $dbPort = [int]$cfg.port }
        if ($cfg.user)     { $dbUser = [string]$cfg.user }
        if ($cfg.database) { $dbName = [string]$cfg.database }
        Ok "db.local.json found: $cfgPath"
        Write-Host "       $dbUser@$dbHost`:$dbPort / $dbName"
    } catch {
        Bad "db.local.json is not valid JSON: $($_.Exception.Message)"
    }
} else {
    Warn "db.local.json missing at $cfgPath (start scripts will use defaults)"
}

# --- 5. Listen port free ---
$busy = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($busy) {
    Bad "port $Port is already in use (GatewayMain may already be running)"
} else {
    Ok "port $Port is free"
}

# --- 6. Ping / reach DB ---
Write-Host ""
Write-Host "--- DB reachability ($dbHost) ---"
$ping = Test-Connection -ComputerName $dbHost -Count 2 -Quiet -ErrorAction SilentlyContinue
if ($ping) {
    Ok "ICMP ping $dbHost ok"
} else {
    Warn "ICMP ping $dbHost failed (may be blocked; try TCP next)"
}

$tcpOk = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $iar = $client.BeginConnect($dbHost, $dbPort, $null, $null)
    $waited = $iar.AsyncWaitHandle.WaitOne(5000, $false)
    if ($waited -and $client.Connected) {
        $tcpOk = $true
        Ok "TCP $dbHost`:$dbPort open (MySQL/MariaDB reachable)"
    } else {
        Bad "TCP $dbHost`:$dbPort connect failed/timeout"
    }
    $client.Close()
} catch {
    Bad "TCP $dbHost`:$dbPort error: $($_.Exception.Message)"
}

if ($tcpOk -and -not $ping) {
    Warn "TCP is open but ICMP is blocked — DB is still usable"
}

Write-Host ""
if ($fail -eq 0) {
    Write-Host "All checks passed." -ForegroundColor Green
    exit 0
} else {
    Write-Host "$fail check(s) failed." -ForegroundColor Red
    exit 1
}
