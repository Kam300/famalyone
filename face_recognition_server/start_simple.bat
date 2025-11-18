@echo off
echo ========================================
echo Face Recognition Server (DEMO)
echo ========================================
echo.
echo Установка зависимостей...
pip install -r requirements_simple.txt
echo.
echo Запуск сервера...
echo.
python server_simple.py
pause
