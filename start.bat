@echo off
setlocal EnableExtensions
rem ============================================================
rem  CSA Java gateway one-click start (local ops tooling).
rem  DB credentials are read from config\db.local.json when
rem  present; otherwise the defaults below are used.
rem  CHANGE the default password if your local DB differs.
rem ============================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

rem --- JAVA_HOME: prefer E:\tools\jdk-21.0.12.1+1 when present ---
set "JDK_HOME=E:\tools\jdk-21.0.12.1+1"
if exist "%JDK_HOME%\bin\java.exe" set "JAVA_HOME=%JDK_HOME%"

if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    goto :have_java
  )
)
where java >nul 2>&1
if errorlevel 1 (
  echo [ERROR] java not found. Install JDK 21 at %JDK_HOME% or set JAVA_HOME.
  exit /b 1
)
set "JAVA_EXE=java"

:have_java

rem --- CSA_DB_* defaults (change password here if not using config\db.local.json) ---
set "CSA_DB_HOST=192.168.2.100"
set "CSA_DB_PORT=3306"
set "CSA_DB_USER=csa"
set "CSA_DB_PASS=0OKM)okm123"
set "CSA_DB_NAME=csa"

rem --- Prefer config\db.local.json when present ---
set "CFG=%ROOT%\config\db.local.json"
if not exist "%CFG%" goto :run
echo [INFO] Loading DB settings from %CFG%
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "(ConvertFrom-Json (Get-Content -Raw -Encoding UTF8 -LiteralPath '%CFG%')).host"`) do set "CSA_DB_HOST=%%A"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "(ConvertFrom-Json (Get-Content -Raw -Encoding UTF8 -LiteralPath '%CFG%')).port"`) do set "CSA_DB_PORT=%%A"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "(ConvertFrom-Json (Get-Content -Raw -Encoding UTF8 -LiteralPath '%CFG%')).user"`) do set "CSA_DB_USER=%%A"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "(ConvertFrom-Json (Get-Content -Raw -Encoding UTF8 -LiteralPath '%CFG%')).password"`) do set "CSA_DB_PASS=%%A"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "(ConvertFrom-Json (Get-Content -Raw -Encoding UTF8 -LiteralPath '%CFG%')).database"`) do set "CSA_DB_NAME=%%A"

:run
set "CP=%ROOT%\java\out;E:\tools\jars\mysql-connector-j.jar"
set "PORT=1234"
if not "%~1"=="" set "PORT=%~1"

echo [INFO] JAVA_HOME=%JAVA_HOME%
echo [INFO] DB=%CSA_DB_USER%@%CSA_DB_HOST%:%CSA_DB_PORT%/%CSA_DB_NAME%
echo [INFO] classpath=%CP%
echo [INFO] Starting csa.client.GatewayMain on port %PORT% ...

"%JAVA_EXE%" -cp "%CP%" csa.client.GatewayMain %PORT%
exit /b %ERRORLEVEL%
