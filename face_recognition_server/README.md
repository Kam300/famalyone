# Face Recognition Server

Python сервер для распознавания лиц в приложении "Семейное Древо".

## Установка

### 1. Установите Python 3.8+
```bash
python --version
```

### 2. Установите зависимости
```bash
pip install -r requirements.txt
```

### 3. Запустите сервер
```bash
python server.py
```

Сервер запустится на `http://localhost:5000`

## API Endpoints

### 1. Проверка работоспособности
```
GET /health
```

**Ответ:**
```json
{
  "status": "ok",
  "members_count": 5
}
```

### 2. Регистрация эталонного фото
```
POST /register_face
```

**Параметры:**
```json
{
  "member_id": "123",
  "member_name": "Иван Иванов",
  "image": "base64_encoded_image"
}
```

**Ответ:**
```json
{
  "success": true,
  "message": "Лицо Иван Иванов успешно зарегистрировано",
  "member_id": "123"
}
```

### 3. Распознавание лица
```
POST /recognize_face
```

**Параметры:**
```json
{
  "image": "base64_encoded_image",
  "threshold": 0.6
}
```

**Ответ:**
```json
{
  "success": true,
  "faces_count": 2,
  "recognized_count": 2,
  "results": [
    {
      "member_id": "123",
      "member_name": "Иван Иванов",
      "confidence": 0.85,
      "location": {
        "top": 100,
        "right": 200,
        "bottom": 300,
        "left": 50
      }
    }
  ]
}
```

### 4. Удаление лица
```
DELETE /delete_face/<member_id>
```

**Ответ:**
```json
{
  "success": true,
  "message": "Лицо успешно удалено"
}
```

### 5. Список зарегистрированных лиц
```
GET /list_faces
```

**Ответ:**
```json
{
  "success": true,
  "count": 5,
  "faces": [
    {
      "member_id": "123",
      "member_name": "Иван Иванов"
    }
  ]
}
```

## Структура папок

```
face_recognition_server/
├── server.py              # Основной сервер
├── requirements.txt       # Зависимости
├── reference_photos/      # Эталонные фото
├── uploaded_photos/       # Загруженные фото
└── face_encodings.json    # Сохраненные кодировки
```

## Настройка

### Изменение порта
В файле `server.py`:
```python
app.run(host='0.0.0.0', port=5000, debug=True)
```

### Изменение порога распознавания
По умолчанию: 0.6 (чем меньше, тем строже)
```python
threshold = data.get('threshold', 0.6)
```

## Troubleshooting

### Ошибка установки dlib
```bash
# Windows
pip install cmake
pip install dlib

# Linux
sudo apt-get install cmake
sudo apt-get install libboost-all-dev
pip install dlib

# macOS
brew install cmake
brew install boost
pip install dlib
```

### Ошибка "No module named 'face_recognition'"
```bash
pip install face-recognition
```

### Сервер не запускается
Проверьте, что порт 5000 свободен:
```bash
# Windows
netstat -ano | findstr :5000

# Linux/macOS
lsof -i :5000
```

## Производительность

- Регистрация лица: ~1-2 секунды
- Распознавание: ~0.5-1 секунда на лицо
- Память: ~100-200 MB

## Безопасность

⚠️ **Важно:**
- Используйте HTTPS в production
- Добавьте аутентификацию
- Ограничьте размер загружаемых файлов
- Используйте rate limiting

## Лицензия

MIT License
