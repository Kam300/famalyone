#!/usr/bin/env python3
"""
Тестовый скрипт для регистрации лица на сервере
"""
import requests
import base64
import sys

def register_face(image_path, member_id, member_name, server_url="http://127.0.0.1:5000"):
    """Регистрирует лицо на сервере"""
    
    # Читаем изображение
    with open(image_path, 'rb') as f:
        image_data = f.read()
    
    # Конвертируем в base64
    base64_image = base64.b64encode(image_data).decode('utf-8')
    
    # Отправляем запрос
    url = f"{server_url}/register_face"
    payload = {
        "member_id": str(member_id),
        "member_name": member_name,
        "image": base64_image
    }
    
    print(f"📤 Отправляем запрос на {url}")
    print(f"👤 ID: {member_id}, Имя: {member_name}")
    
    response = requests.post(url, json=payload)
    
    if response.status_code == 200:
        result = response.json()
        if result.get('success'):
            print(f"✅ Успех: {result.get('message')}")
            return True
        else:
            print(f"❌ Ошибка: {result.get('error')}")
            return False
    else:
        print(f"❌ HTTP ошибка: {response.status_code}")
        print(response.text)
        return False

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Использование: python test_register.py <путь_к_фото> <member_id> <имя>")
        print("Пример: python test_register.py photo.jpg 1 'Иван Иванов'")
        sys.exit(1)
    
    image_path = sys.argv[1]
    member_id = sys.argv[2]
    member_name = sys.argv[3]
    
    register_face(image_path, member_id, member_name)
