"""
Face Recognition Server для приложения Семейное Древо
Использует face_recognition для распознавания лиц
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import face_recognition
import numpy as np
import base64
import io
from PIL import Image
import os
import json
import logging

app = Flask(__name__)
CORS(app)

# Настройка логирования
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Папки для хранения данных
REFERENCE_PHOTOS_DIR = "reference_photos"
UPLOADED_PHOTOS_DIR = "uploaded_photos"
ENCODINGS_FILE = "face_encodings.json"

# Создаем папки если их нет
os.makedirs(REFERENCE_PHOTOS_DIR, exist_ok=True)
os.makedirs(UPLOADED_PHOTOS_DIR, exist_ok=True)

# Хранилище кодировок лиц
face_encodings_db = {}


def load_encodings():
    """Загрузка сохраненных кодировок лиц"""
    global face_encodings_db
    if os.path.exists(ENCODINGS_FILE):
        try:
            with open(ENCODINGS_FILE, 'r') as f:
                data = json.load(f)
                # Конвертируем списки обратно в numpy arrays
                face_encodings_db = {
                    member_id: {
                        'name': info['name'],
                        'encoding': np.array(info['encoding'])
                    }
                    for member_id, info in data.items()
                }
            logger.info(f"Загружено {len(face_encodings_db)} кодировок лиц")
        except Exception as e:
            logger.error(f"Ошибка загрузки кодировок: {e}")
            face_encodings_db = {}


def save_encodings():
    """Сохранение кодировок лиц"""
    try:
        # Конвертируем numpy arrays в списки для JSON
        data = {
            member_id: {
                'name': info['name'],
                'encoding': info['encoding'].tolist()
            }
            for member_id, info in face_encodings_db.items()
        }
        with open(ENCODINGS_FILE, 'w') as f:
            json.dump(data, f)
        logger.info("Кодировки сохранены")
    except Exception as e:
        logger.error(f"Ошибка сохранения кодировок: {e}")


def decode_base64_image(base64_string):
    """Декодирование base64 изображения"""
    try:
        # Убираем префикс data:image если есть
        if ',' in base64_string:
            base64_string = base64_string.split(',')[1]
        
        image_data = base64.b64decode(base64_string)
        image = Image.open(io.BytesIO(image_data))
        
        # Конвертируем в RGB если нужно
        if image.mode != 'RGB':
            image = image.convert('RGB')
        
        return np.array(image)
    except Exception as e:
        logger.error(f"Ошибка декодирования изображения: {e}")
        return None


@app.route('/health', methods=['GET'])
def health_check():
    """Проверка работоспособности сервера"""
    return jsonify({
        'status': 'ok',
        'members_count': len(face_encodings_db)
    })


@app.route('/register_face', methods=['POST'])
def register_face():
    """
    Регистрация эталонного фото члена семьи
    
    Параметры:
    - member_id: ID члена семьи
    - member_name: Имя члена семьи
    - image: base64 изображение
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
        
        # Находим лица на изображении
        face_locations = face_recognition.face_locations(image)
        
        if len(face_locations) == 0:
            return jsonify({
                'success': False,
                'error': 'На фото не обнаружено лиц'
            }), 400
        
        if len(face_locations) > 1:
            return jsonify({
                'success': False,
                'error': 'На фото обнаружено несколько лиц. Используйте фото с одним человеком'
            }), 400
        
        # Получаем кодировку лица
        face_encodings = face_recognition.face_encodings(image, face_locations)
        
        if len(face_encodings) == 0:
            return jsonify({
                'success': False,
                'error': 'Не удалось получить кодировку лица'
            }), 400
        
        # Сохраняем кодировку
        face_encodings_db[str(member_id)] = {
            'name': member_name,
            'encoding': face_encodings[0]
        }
        
        # Сохраняем эталонное фото
        photo_path = os.path.join(REFERENCE_PHOTOS_DIR, f"{member_id}.jpg")
        Image.fromarray(image).save(photo_path)
        
        # Сохраняем в файл
        save_encodings()
        
        logger.info(f"Зарегистрировано лицо для {member_name} (ID: {member_id})")
        
        return jsonify({
            'success': True,
            'message': f'Лицо {member_name} успешно зарегистрировано',
            'member_id': member_id
        })
        
    except Exception as e:
        logger.error(f"Ошибка регистрации лица: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/recognize_face', methods=['POST'])
def recognize_face():
    """
    Распознавание лица на фото
    
    Параметры:
    - image: base64 изображение
    - threshold: порог совпадения (по умолчанию 0.6)
    """
    try:
        data = request.json
        image_base64 = data.get('image')
        threshold = data.get('threshold', 0.6)
        
        if not image_base64:
            return jsonify({
                'success': False,
                'error': 'Отсутствует изображение'
            }), 400
        
        if len(face_encodings_db) == 0:
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
        
        # Находим лица на изображении
        face_locations = face_recognition.face_locations(image)
        
        if len(face_locations) == 0:
            return jsonify({
                'success': False,
                'error': 'На фото не обнаружено лиц'
            }), 400
        
        # Получаем кодировки всех лиц на фото
        face_encodings = face_recognition.face_encodings(image, face_locations)
        
        # Результаты распознавания
        results = []
        
        # Получаем известные кодировки
        known_encodings = [info['encoding'] for info in face_encodings_db.values()]
        known_ids = list(face_encodings_db.keys())
        known_names = [info['name'] for info in face_encodings_db.values()]
        
        # Проверяем каждое лицо на фото
        for face_encoding, face_location in zip(face_encodings, face_locations):
            # Сравниваем с известными лицами
            matches = face_recognition.compare_faces(
                known_encodings, 
                face_encoding, 
                tolerance=threshold
            )
            
            # Вычисляем расстояния
            face_distances = face_recognition.face_distance(known_encodings, face_encoding)
            
            if True in matches:
                # Находим лучшее совпадение
                best_match_index = np.argmin(face_distances)
                
                if matches[best_match_index]:
                    member_id = known_ids[best_match_index]
                    member_name = known_names[best_match_index]
                    confidence = 1 - face_distances[best_match_index]
                    
                    results.append({
                        'member_id': member_id,
                        'member_name': member_name,
                        'confidence': float(confidence),
                        'location': {
                            'top': face_location[0],
                            'right': face_location[1],
                            'bottom': face_location[2],
                            'left': face_location[3]
                        }
                    })
        
        if len(results) == 0:
            return jsonify({
                'success': False,
                'error': 'Лица не распознаны. Возможно, этих людей нет в базе',
                'faces_found': len(face_locations)
            })
        
        logger.info(f"Распознано {len(results)} лиц")
        
        return jsonify({
            'success': True,
            'faces_count': len(face_locations),
            'recognized_count': len(results),
            'results': results
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
        if str(member_id) not in face_encodings_db:
            return jsonify({
                'success': False,
                'error': 'Член семьи не найден'
            }), 404
        
        # Удаляем из базы
        del face_encodings_db[str(member_id)]
        
        # Удаляем файл фото
        photo_path = os.path.join(REFERENCE_PHOTOS_DIR, f"{member_id}.jpg")
        if os.path.exists(photo_path):
            os.remove(photo_path)
        
        # Сохраняем изменения
        save_encodings()
        
        logger.info(f"Удалено лицо для ID: {member_id}")
        
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
            for member_id, info in face_encodings_db.items()
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
    # Загружаем сохраненные кодировки при запуске
    load_encodings()
    
    # Запускаем сервер
    logger.info("Запуск Face Recognition Server...")
    logger.info(f"Загружено {len(face_encodings_db)} лиц")
    
    app.run(host='0.0.0.0', port=5000, debug=True)
