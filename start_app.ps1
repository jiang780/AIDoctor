$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# 密钥从本地 config.properties 读取（该文件已被 .gitignore 忽略，不会上传）
# 请在本机 config.properties 中配置：DEEPSEEK_API_KEY=你的Key
function Get-ConfigValue($key) {
    if (Test-Path 'config.properties') {
        $line = Get-Content 'config.properties' | Where-Object { $_ -match "^\s*$key\s*=" }
        if ($line) { return ($line -split '=', 2)[1].Trim() }
    }
    return $null
}
$dsKey = Get-ConfigValue 'DEEPSEEK_API_KEY'
if ($dsKey) { $env:DEEPSEEK_API_KEY = $dsKey } else { Write-Host '[WARN] 未配置 DEEPSEEK_API_KEY，AI 问诊功能将不可用' }
$oaKey = Get-ConfigValue 'OPENAI_API_KEY'
if ($oaKey) { $env:OPENAI_API_KEY = $oaKey }
$env:DB_URL = 'jdbc:mysql://localhost:3306/ai_doctor?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:DB_USER = 'root'
$dbPw = Get-ConfigValue 'DB_PASSWORD'
if ($dbPw) { $env:DB_PASSWORD = $dbPw } else { Write-Host '[WARN] 未配置 DB_PASSWORD，数据库连接可能失败' }

if (-not (Test-Path 'logs')) {
    New-Item -ItemType Directory -Path 'logs' | Out-Null
}

if (Test-Path 'app.pid') {
    $oldPid = (Get-Content 'app.pid' -Raw).Trim()
    if ($oldPid -and $oldPid -match '^\d+$') {
        Stop-Process -Id ([int]$oldPid) -Force -ErrorAction SilentlyContinue
    }
    Remove-Item 'app.pid' -Force -ErrorAction SilentlyContinue
}

$getJavaProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
foreach ($p in $getJavaProcs) {
    if ($p.CommandLine -match 'Main') {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

$buildOutput = & javac -cp '.\libs\mysql-connector-j-8.4.0.jar' -d out .\src\Main.java 2>&1
if ($LASTEXITCODE -ne 0) {
    $buildOutput | Out-File -FilePath '.\logs\build.log' -Encoding utf8
    Write-Host '[ERROR] Build failed. Check logs\build.log'
    exit 1
}

$cp = "$root\out;$root\libs\mysql-connector-j-8.4.0.jar"
$proc = Start-Process -FilePath 'java' -ArgumentList @('-cp', $cp, 'Main') -WorkingDirectory $root -PassThru
$proc.Id | Set-Content -Path 'app.pid'

Write-Host "====================================================="
Write-Host "AI Doctor started"
Write-Host "Access: http://localhost:8080/login.html"
Write-Host "Login: admin / 123456"
Write-Host "Stop service with: stop_app.bat"
Write-Host "====================================================="
