@echo off
echo ========================================
echo   FamilyOne Combined Server + ngrok
echo ========================================
echo.

cd /d "%~dp0"

:: Запускаем Combined Server в новом окне
echo [1/2] Запуск Combined Server (порт 5000)...
start "FamilyOne Combined Server" cmd /k "python combined_server.py"

timeout /t 3 /nobreak > nul

:: Запускаем ngrok
echo [2/2] Запуск ngrok туннеля...
echo.
echo ==========================================
echo   Скопируйте URL из ngrok для приложения
echo ==========================================
echo.

ngrok http 5000

pause
