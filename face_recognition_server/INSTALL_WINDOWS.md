# Установка Face Recognition Server на Windows

## Шаг 1: Установите Python 3.8-3.10

⚠️ **Важно:** face_recognition лучше работает с Python 3.8-3.10

Скачайте с https://www.python.org/downloads/

## Шаг 2: Установите Visual Studio Build Tools

face_recognition требует компилятор C++.

### Вариант A: Visual Studio Build Tools (рекомендуется)
1. Скачайте: https://visualstudio.microsoft.com/visual-cpp-build-tools/
2. Установите "Desktop development with C++"

### Вариант B: Visual Studio Community
1. Скачайте: https://visualstudio.microsoft.com/
2. При установке выберите "Desktop development with C++"

## Шаг 3: Установите CMake

```powershell
# Через pip
pip install cmake
```

Или скачайте с https://cmake.org/download/

## Шаг 4: Установите dlib

```powershell
pip install dlib
```

Если возникает ошибка, попробуйте:
```powershell
pip install dlib-binary
```

## Шаг 5: Установите face_recognition

```powershell
pip install face-recognition
```

## Шаг 6: Установите остальные зависимости

```powershell
pip install flask flask-cors numpy Pillow
```

## Шаг 7: Проверка установки

```powershell
python -c "import face_recognition; print('OK')"
```

Если видите "OK" - всё установлено правильно!

## Шаг 8: Запуск сервера

```powershell
cd face_recognition_server
python server.py
```

## Альтернатива: Использование готовых wheel файлов

Если установка не работает, скачайте готовые .whl файлы:

1. dlib: https://github.com/z-mahmud22/Dlib_Windows_Python3.x
2. Установите:
```powershell
pip install dlib-19.24.0-cp310-cp310-win_amd64.whl
pip install face-recognition
```

## Troubleshooting

### Ошибка "Microsoft Visual C++ 14.0 is required"
- Установите Visual Studio Build Tools (см. Шаг 2)

### Ошибка при установке dlib
```powershell
# Попробуйте установить готовый wheel
pip install https://github.com/jloh02/dlib/releases/download/v19.22/dlib-19.22.99-cp310-cp310-win_amd64.whl
```

### Ошибка "No module named 'face_recognition'"
```powershell
# Убедитесь, что используете правильный Python
python --version
pip --version

# Переустановите
pip uninstall face-recognition
pip install face-recognition
```

### Сервер не запускается
```powershell
# Проверьте все зависимости
pip list | findstr face
pip list | findstr dlib
pip list | findstr flask
```

## Быстрая установка (если всё работает)

```powershell
pip install cmake
pip install dlib
pip install face-recognition
pip install flask flask-cors numpy Pillow
```

## Проверка версий

```powershell
python --version
pip list
```

Должны быть установлены:
- dlib >= 19.22
- face-recognition >= 1.3.0
- flask >= 3.0.0
- numpy >= 1.24.0
- Pillow >= 10.0.0
