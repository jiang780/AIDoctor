@echo off
chcp 65001
cd /d "%~dp0"
if exist "app.pid" (
  set /p oldpid=<app.pid
  if not "%oldpid%"=="" taskkill /PID %oldpid% /F >nul 2>&1
  del /f /q app.pid >nul 2>&1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$procs = Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" -ErrorAction SilentlyContinue; foreach ($p in $procs) { if ($p.CommandLine -match 'Main') { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue } }"
echo AIDoctor service stopped.
exit /b 0
