@echo off
chcp 65001 >nul
cd /d "%~dp0"

REM ===== 无需管理员权限的一键启动脚本 =====
REM 先启动“用户自有”的 MySQL 实例（数据目录在 C:\Users\R\mysql-data，避免 ProgramData 无写权限问题）
REM 再启动 AIDoctor 应用。

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 9.3\bin"
set "MYSQLD=%MYSQL_BIN%\mysqld.exe"
set "MYSQL_CLIENT=%MYSQL_BIN%\mysql.exe"
set "MYSQL_DATA=D:\mysql-data"

REM 1) 首次初始化 MySQL 数据目录（仅需一次，无需管理员）
if not exist "%MYSQL_DATA%" (
  echo 首次初始化 MySQL 数据目录（无需管理员）...
  "%MYSQLD%" --initialize-insecure --datadir="%MYSQL_DATA%"
)

REM 2) 后台启动 MySQL（无需管理员）
echo 启动 MySQL...
start "MySQL-Server" "%MYSQLD%" --datadir="%MYSQL_DATA%" --port=3306 --console

REM 3) 等待 MySQL 就绪（最多约 30 秒）
echo 等待 MySQL 就绪...
set /a n=0
:waitloop
"%MYSQL_CLIENT%" -u root -p1234567890 -h 127.0.0.1 -P 3306 -e "SELECT 1" >nul 2>&1
if %errorlevel%==0 goto ready
%SystemRoot%\System32\ping.exe -n 3 127.0.0.1 >nul
set /a n+=1
if %n% lss 15 goto waitloop
echo [错误] MySQL 启动超时，请检查。
pause
exit /b 1

:ready
REM 4) 确保 ai_doctor 库存在（应用需要该库已存在才能连上建表）
"%MYSQL_CLIENT%" -u root -p1234567890 -h 127.0.0.1 -P 3306 -e "CREATE DATABASE IF NOT EXISTS ai_doctor CHARACTER SET utf8mb4;" >nul 2>&1

REM 5) 启动 AIDoctor 应用
echo 启动 AIDoctor 应用...
call start_app.bat
