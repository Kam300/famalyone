@echo off
echo ========================================
echo   Запуск серверов FamilyOne + ngrok
echo ========================================
echo.

:: Переходим в директорию скрипта
cd /d "%~dp0"

:: Запускаем Face Recognition Server в новом окне
echo [1/3] Запуск Face Recognition Server (порт 5000)...
start "Face Recognition Server" cmd /k "python server.py"

:: Небольшая пауза
timeout /t 2 /nobreak > nul

:: Запускаем PDF Server в новом окне
echo [2/3] Запуск PDF Server (порт 5001)...
start "PDF Server" cmd /k "python pdf_server.py"

:: Небольшая пауза
timeout /t 2 /nobreak > nul

:: Запускаем ngrok с конфигурацией
echo [3/3] Запуск ngrok туннелей...
echo.
echo После запуска ngrok, вы увидите публичные URL-адреса:
echo   - face_recognition: для сервера распознавания лиц
echo   - pdf_server: для PDF-сервера
echo.

:: Запускаем ngrok, используя локальный конфиг
ngrok start --config ngrok.yml --all

pause
