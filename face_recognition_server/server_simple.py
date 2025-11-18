"""
Упрощенная версия Face Recognition Server для тестирования
Без face_recognition - использует случайное распознавание для демонстрации
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import base64
import io
from PIL import Image
import os
import json
import logging
import random

app = Flask(__name__)
CORS(app)

# Настройка логирования
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Папки для хранения данных
REFERENCE_PHOTOS_DIR = "reference_photos"
UPLOADED_PHOTOS_DIR = "uploaded_photos"
MEMBERS_FILE = "members.json"

# Создаем папки если их нет
os.makedirs(REFERENCE_PHOTOS_DIR, exist_ok=True)
os.makedirs(UPLOADED_PHOTOS_DIR, exist_ok=True)

# Хранилище членов семьи
members_db = {}


def load_members():
    """Загрузка сохраненных членов семьи"""
    global members_db
    if os.path.exists(MEMBERS_FILE):
        try:
            with open(MEMBERS_FILE, 'r', encoding='utf-8') as f:
                members_db = json.load(f)
            logger.info(f"Загружено {len(members_db)} членов семьи")
        except Exception as e:
            logger.error(f"Ошибка загрузки: {e}")
            members_db = {}


def save_members():
    """Сохранение членов семьи"""
    try:
        with open(MEMBERS_FILE, 'w', encoding='utf-8') as f:
            json.dump(members_db, f, ensure_ascii=False, indent=2)
        logger.info("Члены семьи сохранены")
    except Exception as e:
        logger.error(f"Ошибка сохранения: {e}")


def decode_base64_image(base64_string):
    """Декодирование base64 изображения"""
    try:
        if ',' in base64_string:
            base64_string = base64_string.split(',')[1]
        
        image_data = base64.b64decode(base64_string)
        image = Image.open(io.BytesIO(image_data))
        
        if image.mode != 'RGB':
            image = image.convert('RGB')
        
        return image
    except Exception as e:
        logger.error(f"Ошибка декодирования изображения: {e}")
        return None


@app.route('/health', methods=['GET'])
def health_check():
    """Проверка работоспособности сервера"""
    return jsonify({
        'status': 'ok',
        'members_count': len(members_db),
        'mode': 'simple_demo'
    })


@app.route('/register_face', methods=['POST'])
def register_face():
    """
    Регистрация эталонного фото члена семьи (упрощенная версия)
    """
    try:
        data = request.json
        member_id = data.get('member_id')
        member_name = data.get('member_name')
        image_base64 = data.get('image')
        
        if not all([member_id, member_name, image_base64]):
            return jsonify({
                'success': False,
                'error': 'Отсутствуют обязательные параметры'
            }), 400
        
        # Декодируем изображение
        image = decode_base64_image(image_base64)
        if image is None:
            return jsonify({
                'success': False,
                'error': 'Не удалось декодировать изображение'
            }), 400
        
        # Сохраняем информацию о члене семьи
        members_db[str(member_id)] = {
            'name': member_name,
            'registered': True
        }
        
        # Сохраняем эталонное фото
        photo_path = os.path.join(REFERENCE_PHOTOS_DIR, f"{member_id}.jpg")
        image.save(photo_path)
        
        # Сохраняем в файл
        save_members()
        
        logger.info(f"Зарегистрирован {member_name} (ID: {member_id})")
        
        return jsonify({
            'success': True,
            'message': f'Лицо {member_name} успешно зарегистрировано',
            'member_id': member_id
        })
        
    except Exception as e:
        logger.error(f"Ошибка регистрации: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/recognize_face', methods=['POST'])
def recognize_face():
    """
    Распознавание лица на фото (упрощенная версия - случайное распознавание)
    """
    try:
        data = request.json
        image_base64 = data.get('image')
        
        if not image_base64:
            return jsonify({
                'success': False,
                'error': 'Отсутствует изображение'
            }), 400
        
        if len(members_db) == 0:
            return jsonify({
                'success': False,
                'error': 'Нет зарегистрированных лиц'
            }), 400
        
        # Декодируем изображение
        image = decode_base64_image(image_base64)
        if image is None:
            return jsonify({
                'success': False,
                'error': 'Не удалось декодировать изображение'
            }), 400
        
        # ДЕМО: Случайно выбираем 1-2 членов семьи
        # В реальной версии здесь будет face_recognition
        num_faces = random.randint(1, min(2, len(members_db)))
        selected_members = random.sample(list(members_db.items()), num_faces)
        
        results = []
        for member_id, member_info in selected_members:
            confidence = random.uniform(0.75, 0.95)  # Случайная уверенность
            
            results.append({
                'member_id': member_id,
                'member_name': member_info['name'],
                'confidence': confidence,
                'location': {
                    'top': random.randint(50, 150),
                    'right': random.randint(200, 300),
                    'bottom': random.randint(250, 350),
                    'left': random.randint(50, 150)
                }
            })
        
        logger.info(f"Распознано {len(results)} лиц (DEMO режим)")
        
        return jsonify({
            'success': True,
            'faces_count': num_faces,
            'recognized_count': len(results),
            'results': results,
            'demo_mode': True
        })
        
    except Exception as e:
        logger.error(f"Ошибка распознавания: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/delete_face/<member_id>', methods=['DELETE'])
def delete_face(member_id):
    """Удаление эталонного фото члена семьи"""
    try:
        if str(member_id) not in members_db:
            return jsonify({
                'success': False,
                'error': 'Член семьи не найден'
            }), 404
        
        # Удаляем из базы
        del members_db[str(member_id)]
        
        # Удаляем файл фото
        photo_path = os.path.join(REFERENCE_PHOTOS_DIR, f"{member_id}.jpg")
        if os.path.exists(photo_path):
            os.remove(photo_path)
        
        # Сохраняем изменения
        save_members()
        
        logger.info(f"Удален член семьи ID: {member_id}")
        
        return jsonify({
            'success': True,
            'message': 'Лицо успешно удалено'
        })
        
    except Exception as e:
        logger.error(f"Ошибка удаления: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/list_faces', methods=['GET'])
def list_faces():
    """Получение списка зарегистрированных лиц"""
    try:
        faces = [
            {
                'member_id': member_id,
                'member_name': info['name']
            }
            for member_id, info in members_db.items()
        ]
        
        return jsonify({
            'success': True,
            'count': len(faces),
            'faces': faces
        })
        
    except Exception as e:
        logger.error(f"Ошибка получения списка: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


if __name__ == '__main__':
    # Загружаем сохраненных членов при запуске
    load_members()
    
    # Запускаем сервер
    logger.info("=" * 60)
    logger.info("Запуск Face Recognition Server (DEMO режим)")
    logger.info("⚠️  Это упрощенная версия без реального распознавания")
    logger.info("⚠️  Для полной версии установите face_recognition")
    logger.info("=" * 60)
    logger.info(f"Загружено {len(members_db)} членов семьи")
    logger.info("Сервер запущен на http://localhost:5000")
    
    app.run(host='0.0.0.0', port=5000, debug=True)
