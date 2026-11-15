@echo off
REM AIDoctor 后端 Checkstyle 校验（Windows）
REM 首次运行会自动下载 Checkstyle jar（约 20MB），需联网。

setlocal
set CHECKSTYLE_VERSION=10.17.0
set JAR=tools\checkstyle\checkstyle-%CHECKSTYLE_VERSION%-all.jar
set URL=https://github.com/checkstyle/checkstyle/releases/download/checkstyle-%CHECKSTYLE_VERSION%\checkstyle-%CHECKSTYLE_VERSION%-all.jar

if not exist "%JAR%" (
  echo [INFO] 未找到 Checkstyle，正在下载 %CHECKSTYLE_VERSION% ...
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%JAR%'"
)

if not exist "%JAR%" (
  echo [ERROR] 下载失败，请手动下载并放到 %JAR%
  echo          %URL%
  exit /b 1
)

echo [INFO] 运行 Checkstyle（配置 checkstyle.xml，检查 src）...
java -jar "%JAR%" -c checkstyle.xml src
endlocal
