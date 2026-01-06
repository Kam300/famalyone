# PowerShell скрипт для запуска серверов FamilyOne + ngrok
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Запуск серверов FamilyOne + ngrok" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Переходим в директорию скрипта
Set-Location $PSScriptRoot

# Запускаем Face Recognition Server
Write-Host "[1/3] Запуск Face Recognition Server (порт 5000)..." -ForegroundColor Green
Start-Process -FilePath "python" -ArgumentList "server.py" -WindowStyle Normal

Start-Sleep -Seconds 2

# Запускаем PDF Server
Write-Host "[2/3] Запуск PDF Server (порт 5001)..." -ForegroundColor Green
Start-Process -FilePath "python" -ArgumentList "pdf_server.py" -WindowStyle Normal

Start-Sleep -Seconds 2

# Запускаем ngrok
Write-Host "[3/3] Запуск ngrok туннелей..." -ForegroundColor Green
Write-Host ""
Write-Host "После запуска ngrok, скопируйте публичные URL-адреса:" -ForegroundColor Yellow
Write-Host "  - face_recognition: для сервера распознавания лиц" -ForegroundColor Yellow
Write-Host "  - pdf_server: для PDF-сервера" -ForegroundColor Yellow
Write-Host ""

# Запускаем ngrok с конфигурацией
ngrok start --config "$PSScriptRoot\ngrok.yml" --all
