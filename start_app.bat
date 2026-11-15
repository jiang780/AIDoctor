@echo off
chcp 65001 >nul
cd /d "%~dp0"
REM 密钥从本地 config.properties 读取（该文件已被 .gitignore 忽略，不会上传）
REM 请在本机 config.properties 中配置：DEEPSEEK_API_KEY=你的Key
if exist "config.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /i "^DEEPSEEK_API_KEY=" config.properties`) do set "DEEPSEEK_API_KEY=%%B"
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /i "^OPENAI_API_KEY=" config.properties`) do set "OPENAI_API_KEY=%%B"
)
if not defined DEEPSEEK_API_KEY (
  echo [WARN] 未配置 DEEPSEEK_API_KEY，AI 问诊功能将不可用；请在 config.properties 中填写
)
REM 可选：配置百度 / 腾讯云 OCR 后，「拍照识药」可直接识别药盒图片上的文字
REM set "BAIDU_OCR_API_KEY=你的API Key"
REM set "BAIDU_OCR_SECRET_KEY=你的Secret Key"
REM set "TENCENT_OCR_SECRET_ID=你的SecretId"
REM set "TENCENT_OCR_SECRET_KEY=你的SecretKey"
set "DB_URL=jdbc:mysql://localhost:3306/ai_doctor?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
set "DB_USER=root"
REM 数据库密码从本地 config.properties 读取（不进版本库）
if exist "config.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /i "^DB_PASSWORD=" config.properties`) do set "DB_PASSWORD=%%B"
)
if not defined DB_PASSWORD (
  echo [WARN] 未配置 DB_PASSWORD，数据库连接可能失败；请在 config.properties 中填写
)
if not exist "logs" mkdir logs

REM ---------- 1. 停掉上一个实例 ----------
if exist "app.pid" (
  set /p oldpid=<app.pid
  if not "%oldpid%"=="" taskkill /PID %oldpid% /F >nul 2>&1
  del /f /q app.pid >nul 2>&1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match 'Main' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

REM ---------- 2. 编译 ----------
echo [1/3] compiling...
javac -encoding UTF-8 -cp "libs\mysql-connector-j-8.4.0.jar" -d out src\Main.java > logs\build.log 2>&1
if errorlevel 1 (
  echo [ERROR] compile failed, see logs\build.log
  exit /b 1
)

REM ---------- 3. 后台启动 ----------
echo [2/3] starting...
start "AIDoctor-API" /min cmd /c "java -cp out;libs\mysql-connector-j-8.4.0.jar Main >> logs\app.log 2>&1"

REM 等待端口就绪（ping 做延时，避免 PATH 里的其它 timeout 干扰）
%SystemRoot%\System32\ping.exe -n 6 127.0.0.1 >nul
netstat -ano | %SystemRoot%\System32\findstr.exe ":8080" | %SystemRoot%\System32\findstr.exe "LISTENING" >nul
if errorlevel 1 (
  echo [WARN] port 8080 not ready, see logs\app.log
  echo [WARN] 若提示数据库连接失败，请先运行 start_all.bat 启动 MySQL
  exit /b 1
)

echo [3/3] ready
echo AIDoctor started: http://localhost:8080/login.html
start "" http://localhost:8080/login.html
exit /b 0
