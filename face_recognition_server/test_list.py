#!/usr/bin/env python3
"""
Скрипт для просмотра зарегистрированных лиц
"""
import requests

def list_faces(server_url="http://127.0.0.1:5000"):
    """Получает список зарегистрированных лиц"""
    
    url = f"{server_url}/list_faces"
    print(f"📤 Запрос к {url}")
    
    response = requests.get(url)
    
    if response.status_code == 200:
        result = response.json()
        if result.get('success'):
            faces = result.get('faces', [])
            print(f"\n✅ Зарегистрировано лиц: {len(faces)}\n")
            
            if faces:
                for i, face in enumerate(faces, 1):
                    print(f"{i}. ID: {face['member_id']}, Имя: {face['member_name']}")
            else:
                print("⚠️ Нет зарегистрированных лиц")
            
            return faces
        else:
            print(f"❌ Ошибка: {result.get('error')}")
            return []
    else:
        print(f"❌ HTTP ошибка: {response.status_code}")
        print(response.text)
        return []

if __name__ == "__main__":
    list_faces()
