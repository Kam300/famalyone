"""
PDF Generation Server для приложения Семейное Древо
Современный дизайн с линиями связей v2
"""

from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, A3, landscape
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from PIL import Image
import base64
import io
import os
import logging
from datetime import datetime

app = Flask(__name__)
CORS(app)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

TEMP_DIR = "temp_pdf"
os.makedirs(TEMP_DIR, exist_ok=True)

# Цвета
PURPLE = (94/255, 67/255, 236/255)
PURPLE_LIGHT = (130/255, 100/255, 255/255)
ORANGE = (255/255, 107/255, 53/255)
LIGHT_BG = (248/255, 246/255, 255/255)
DARK_TEXT = (30/255, 30/255, 30/255)
GRAY_TEXT = (100/255, 100/255, 100/255)
LINE_COLOR = (160/255, 140/255, 200/255)
CARD_BORDER = (180/255, 160/255, 220/255)
WHITE = (1, 1, 1)

def setup_fonts():
    """Настройка шрифтов"""
    font_paths = [
        ("C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/arialbd.ttf"),
        ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
        ("/Library/Fonts/Arial.ttf", "/Library/Fonts/Arial Bold.ttf"),
    ]
    
    for regular, bold in font_paths:
        if os.path.exists(regular):
            try:
                pdfmetrics.registerFont(TTFont('CustomFont', regular))
                if os.path.exists(bold):
                    pdfmetrics.registerFont(TTFont('CustomBold', bold))
                else:
                    pdfmetrics.registerFont(TTFont('CustomBold', regular))
                return 'CustomFont', 'CustomBold'
            except Exception as e:
                logger.warning(f"Ошибка шрифта: {e}")
    
    return 'Helvetica', 'Helvetica-Bold'

FONT_REGULAR, FONT_BOLD = setup_fonts()


@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({'status': 'ok', 'service': 'pdf_generator_v2'})


@app.route('/generate_pdf', methods=['POST'])
def generate_pdf():
    try:
        data = request.json
        members = data.get('members', [])
        page_format = data.get('format', 'A4_LANDSCAPE')
        
        if not members:
            return jsonify({'success': False, 'error': 'Нет данных'}), 400
        
        if page_format == 'A4':
            pagesize = A4
        elif page_format == 'A4_LANDSCAPE':
            pagesize = landscape(A4)
        elif page_format == 'A3':
            pagesize = A3
        elif page_format == 'A3_LANDSCAPE':
            pagesize = landscape(A3)
        else:
            pagesize = landscape(A4)
        
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"family_tree_{timestamp}.pdf"
        filepath = os.path.join(TEMP_DIR, filename)
        
        c = canvas.Canvas(filepath, pagesize=pagesize)
        width, height = pagesize
        
        draw_family_tree(c, members, width, height)
        
        c.save()
        
        return send_file(filepath, mimetype='application/pdf', 
                        as_attachment=True, download_name=filename)
        
    except Exception as e:
        logger.error(f"Ошибка: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'success': False, 'error': str(e)}), 500


def draw_family_tree(c, members, width, height):
    """Рисует семейное древо"""
    
    # Фон страницы
    c.setFillColorRGB(*LIGHT_BG)
    c.rect(0, 0, width, height, fill=1, stroke=0)
    
    # Заголовок
    header_height = draw_header(c, width, height)
    
    # Группируем по поколениям
    generations = group_by_generation(members)
    
    gen_order = [
        ('grandparents', 'Бабушки и Дедушки'),
        ('parents', 'Родители'),
        ('uncles', 'Дяди и Тёти'),
        ('children', 'Дети'),
        ('nephews', 'Племянники'),
        ('grandchildren', 'Внуки'),
        ('other', 'Другие')
    ]
    
    active_gens = [(key, name) for key, name in gen_order if generations.get(key)]
    
    if not active_gens:
        return
    
    # Параметры
    card_width = 130
    card_height = 145
    card_gap_x = 25
    gen_gap_y = 50
    
    # Вычисляем общую высоту
    total_height = len(active_gens) * card_height + (len(active_gens) - 1) * gen_gap_y + len(active_gens) * 25
    start_y = height - header_height - 30
    
    if total_height > start_y - 40:
        # Уменьшаем если не помещается
        scale = (start_y - 40) / total_height
        card_height = int(card_height * scale)
        gen_gap_y = int(gen_gap_y * scale)
    
    card_positions = {}
    current_y = start_y
    
    for gen_idx, (gen_key, gen_name) in enumerate(active_gens):
        gen_members = generations[gen_key]
        
        # Заголовок поколения
        current_y -= 20
        draw_gen_label(c, gen_name, width, current_y + 5)
        
        current_y -= 5
        
        # Карточки
        num_members = len(gen_members)
        total_cards_width = num_members * card_width + (num_members - 1) * card_gap_x
        start_x = (width - total_cards_width) / 2
        
        for i, member in enumerate(gen_members):
            x = start_x + i * (card_width + card_gap_x)
            y = current_y - card_height
            
            draw_member_card(c, member, x, y, card_width, card_height)
            
            member_id = member.get('id')
            card_positions[member_id] = {
                'x_center': x + card_width / 2,
                'y_top': y + card_height,
                'y_bottom': y,
                'y_center': y + card_height / 2
            }
        
        current_y -= card_height + gen_gap_y
    
    # Линии связей (рисуем поверх)
    draw_connections(c, card_positions, members)
    
    # Футер
    draw_footer(c, width)


def draw_header(c, width, height):
    """Заголовок документа"""
    header_h = 70
    
    # Градиентный фон заголовка
    c.setFillColorRGB(*PURPLE)
    c.rect(0, height - header_h, width, header_h, fill=1, stroke=0)
    
    # Декоративная линия
    c.setStrokeColorRGB(*PURPLE_LIGHT)
    c.setLineWidth(3)
    c.line(50, height - header_h, width - 50, height - header_h)
    
    # Заголовок
    c.setFillColorRGB(*WHITE)
    c.setFont(FONT_BOLD, 32)
    c.drawCentredString(width / 2, height - 45, "СЕМЕЙНОЕ ДРЕВО")
    
    # Подзаголовок
    c.setFont(FONT_REGULAR, 11)
    c.setFillColorRGB(0.9, 0.9, 0.95)
    c.drawCentredString(width / 2, height - 62, "Создано в приложении FamilyOne")
    
    return header_h


def draw_gen_label(c, name, width, y):
    """Метка поколения"""
    # Линия слева
    c.setStrokeColorRGB(*LINE_COLOR)
    c.setLineWidth(1.5)
    line_width = 80
    
    text_width = len(name) * 7 + 20
    
    c.line(width/2 - text_width/2 - line_width, y, width/2 - text_width/2 - 10, y)
    c.line(width/2 + text_width/2 + 10, y, width/2 + text_width/2 + line_width, y)
    
    # Текст
    c.setFillColorRGB(*ORANGE)
    c.setFont(FONT_BOLD, 13)
    c.drawCentredString(width / 2, y - 4, name)


def draw_member_card(c, member, x, y, w, h):
    """Карточка члена семьи"""
    
    # Тень
    c.setFillColorRGB(0.85, 0.83, 0.88)
    c.roundRect(x + 4, y - 4, w, h, 10, fill=1, stroke=0)
    
    # Основа карточки
    c.setFillColorRGB(*WHITE)
    c.roundRect(x, y, w, h, 10, fill=1, stroke=0)
    
    # Рамка
    c.setStrokeColorRGB(*CARD_BORDER)
    c.setLineWidth(1.5)
    c.roundRect(x, y, w, h, 10, fill=0, stroke=1)
    
    # Цветная полоса сверху
    c.setFillColorRGB(*PURPLE)
    # Верхняя часть с закруглением
    c.saveState()
    path = c.beginPath()
    path.moveTo(x, y + h - 10)
    path.lineTo(x, y + h - 6)
    path.arcTo(x, y + h - 10, x + 10, y + h, 90, 90)
    path.lineTo(x + w - 10, y + h)
    path.arcTo(x + w - 10, y + h - 10, x + w, y + h, 0, 90)
    path.lineTo(x + w, y + h - 10)
    path.close()
    c.drawPath(path, fill=1, stroke=0)
    c.restoreState()
    
    curr_y = y + h - 20
    
    # Фото
    photo_size = 50
    photo_x = x + (w - photo_size) / 2
    photo_y = curr_y - photo_size
    
    photo_data = member.get('photoBase64')
    if photo_data:
        try:
            draw_photo(c, photo_data, photo_x, photo_y, photo_size)
        except Exception as e:
            logger.warning(f"Фото ошибка: {e}")
            draw_avatar(c, photo_x, photo_y, photo_size)
    else:
        draw_avatar(c, photo_x, photo_y, photo_size)
    
    curr_y = photo_y - 8
    
    # Имя
    c.setFillColorRGB(*DARK_TEXT)
    c.setFont(FONT_BOLD, 11)
    
    name = f"{member.get('lastName', '')} {member.get('firstName', '')}"
    if len(name) > 16:
        name = name[:14] + ".."
    c.drawCentredString(x + w/2, curr_y, name)
    curr_y -= 13
    
    # Отчество
    patronymic = member.get('patronymic', '')
    if patronymic:
        c.setFont(FONT_REGULAR, 9)
        if len(patronymic) > 18:
            patronymic = patronymic[:16] + ".."
        c.drawCentredString(x + w/2, curr_y, patronymic)
        curr_y -= 11
    
    # Роль
    role = get_role_name(member.get('role', 'OTHER'))
    c.setFillColorRGB(*PURPLE)
    c.setFont(FONT_BOLD, 9)
    c.drawCentredString(x + w/2, curr_y, role)
    curr_y -= 12
    
    # Дата
    birth = member.get('birthDate', '')
    if birth:
        c.setFillColorRGB(*GRAY_TEXT)
        c.setFont(FONT_REGULAR, 9)
        c.drawCentredString(x + w/2, curr_y, birth)


def draw_photo(c, photo_data, x, y, size):
    """Рисует круглое фото"""
    if ',' in photo_data:
        photo_data = photo_data.split(',')[1]
    
    img_data = base64.b64decode(photo_data)
    img = Image.open(io.BytesIO(img_data))
    img = img.convert('RGBA')
    
    # Делаем квадратное изображение (обрезаем по центру)
    width, height = img.size
    min_side = min(width, height)
    left = (width - min_side) // 2
    top = (height - min_side) // 2
    img = img.crop((left, top, left + min_side, top + min_side))
    
    # Масштабируем
    img = img.resize((int(size * 3), int(size * 3)), Image.LANCZOS)
    
    # Создаём круглую маску
    mask = Image.new('L', img.size, 0)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, img.size[0], img.size[1]), fill=255)
    
    # Применяем маску
    output = Image.new('RGBA', img.size, (255, 255, 255, 0))
    output.paste(img, (0, 0))
    output.putalpha(mask)
    
    # Сохраняем
    temp_path = os.path.join(TEMP_DIR, f"photo_{hash(photo_data) % 10000}.png")
    output.save(temp_path, 'PNG')
    
    # Рисуем фиолетовую рамку
    c.setFillColorRGB(*PURPLE)
    c.circle(x + size/2, y + size/2, size/2 + 3, fill=1, stroke=0)
    
    # Белый фон под фото
    c.setFillColorRGB(*WHITE)
    c.circle(x + size/2, y + size/2, size/2, fill=1, stroke=0)
    
    # Рисуем фото
    c.drawImage(temp_path, x, y, size, size, mask='auto')
    
    try:
        os.remove(temp_path)
    except:
        pass


def draw_avatar(c, x, y, size):
    """Плейсхолдер"""
    # Круг
    c.setFillColorRGB(0.92, 0.90, 0.96)
    c.circle(x + size/2, y + size/2, size/2, fill=1, stroke=0)
    
    # Рамка
    c.setStrokeColorRGB(*CARD_BORDER)
    c.setLineWidth(2)
    c.circle(x + size/2, y + size/2, size/2, fill=0, stroke=1)
    
    # Иконка человека
    c.setFillColorRGB(*GRAY_TEXT)
    cx, cy = x + size/2, y + size/2
    
    # Голова
    c.circle(cx, cy + 8, 8, fill=1, stroke=0)
    # Тело
    c.ellipse(cx - 12, cy - 18, cx + 12, cy - 2, fill=1, stroke=0)


def draw_connections(c, positions, members):
    """Линии связей"""
    c.setStrokeColorRGB(*LINE_COLOR)
    c.setLineWidth(2)
    
    drawn_pairs = set()
    
    for member in members:
        member_id = member.get('id')
        father_id = member.get('fatherId')
        mother_id = member.get('motherId')
        
        if member_id not in positions:
            continue
        
        child = positions[member_id]
        
        # К отцу
        if father_id and father_id in positions:
            pair = tuple(sorted([member_id, father_id]))
            if pair not in drawn_pairs:
                parent = positions[father_id]
                draw_tree_line(c, parent['x_center'], parent['y_bottom'], 
                              child['x_center'], child['y_top'])
                drawn_pairs.add(pair)
        
        # К матери
        if mother_id and mother_id in positions:
            pair = tuple(sorted([member_id, mother_id]))
            if pair not in drawn_pairs:
                parent = positions[mother_id]
                draw_tree_line(c, parent['x_center'], parent['y_bottom'],
                              child['x_center'], child['y_top'])
                drawn_pairs.add(pair)


def draw_tree_line(c, x1, y1, x2, y2):
    """Рисует линию древа"""
    mid_y = (y1 + y2) / 2
    
    c.setStrokeColorRGB(*LINE_COLOR)
    c.setLineWidth(2)
    
    # Вертикальная от родителя
    c.line(x1, y1, x1, mid_y)
    # Горизонтальная
    c.line(x1, mid_y, x2, mid_y)
    # Вертикальная к ребёнку
    c.line(x2, mid_y, x2, y2)
    
    # Точка соединения
    c.setFillColorRGB(*PURPLE)
    c.circle(x2, y2, 4, fill=1, stroke=0)


def draw_footer(c, width):
    """Футер"""
    c.setFillColorRGB(*GRAY_TEXT)
    c.setFont(FONT_REGULAR, 9)
    date_str = datetime.now().strftime("%d.%m.%Y")
    c.drawCentredString(width / 2, 15, f"Дата создания: {date_str}")
    
    # Линия над футером
    c.setStrokeColorRGB(*LINE_COLOR)
    c.setLineWidth(0.5)
    c.line(50, 30, width - 50, 30)


def get_role_name(role):
    roles = {
        'GRANDFATHER': 'Дедушка', 'GRANDMOTHER': 'Бабушка',
        'FATHER': 'Отец', 'MOTHER': 'Мать',
        'SON': 'Сын', 'DAUGHTER': 'Дочь',
        'BROTHER': 'Брат', 'SISTER': 'Сестра',
        'UNCLE': 'Дядя', 'AUNT': 'Тётя',
        'NEPHEW': 'Племянник', 'NIECE': 'Племянница',
        'GRANDSON': 'Внук', 'GRANDDAUGHTER': 'Внучка',
        'OTHER': 'Родственник'
    }
    return roles.get(role, 'Родственник')


def group_by_generation(members):
    gens = {
        'grandparents': [], 'parents': [], 'uncles': [],
        'children': [], 'nephews': [], 'grandchildren': [], 'other': []
    }
    
    role_map = {
        'GRANDFATHER': 'grandparents', 'GRANDMOTHER': 'grandparents',
        'FATHER': 'parents', 'MOTHER': 'parents',
        'UNCLE': 'uncles', 'AUNT': 'uncles',
        'SON': 'children', 'DAUGHTER': 'children',
        'BROTHER': 'children', 'SISTER': 'children',
        'NEPHEW': 'nephews', 'NIECE': 'nephews',
        'GRANDSON': 'grandchildren', 'GRANDDAUGHTER': 'grandchildren',
        'OTHER': 'other'
    }
    
    for member in members:
        role = member.get('role', 'OTHER')
        gen = role_map.get(role, 'other')
        gens[gen].append(member)
    
    # Группируем пары (муж+жена) вместе
    for gen_key in gens:
        gens[gen_key] = sort_as_couples(gens[gen_key], members)
    
    return gens


def sort_as_couples(gen_members, all_members):
    """Сортирует членов поколения парами (муж+жена рядом)"""
    if len(gen_members) <= 1:
        return gen_members
    
    # Находим пары через общих детей
    couples = find_couples(gen_members, all_members)
    
    result = []
    used_ids = set()
    
    # Сначала добавляем пары
    for m_id, f_id in couples:
        male = next((m for m in gen_members if m.get('id') == m_id), None)
        female = next((m for m in gen_members if m.get('id') == f_id), None)
        
        if male and male.get('id') not in used_ids:
            result.append(male)
            used_ids.add(male.get('id'))
        if female and female.get('id') not in used_ids:
            result.append(female)
            used_ids.add(female.get('id'))
    
    # Добавляем оставшихся (одиночек), сортируя мужчин перед женщинами
    remaining = [m for m in gen_members if m.get('id') not in used_ids]
    remaining.sort(key=lambda m: get_gender_order(m.get('role', 'OTHER')))
    result.extend(remaining)
    
    return result


def find_couples(gen_members, all_members):
    """Находит пары (муж+жена) через общих детей"""
    couples = []
    member_ids = {m.get('id') for m in gen_members}
    
    # Ищем детей, у которых оба родителя в этом поколении
    for member in all_members:
        father_id = member.get('fatherId')
        mother_id = member.get('motherId')
        
        if father_id and mother_id:
            if father_id in member_ids and mother_id in member_ids:
                couple = (father_id, mother_id)
                if couple not in couples:
                    couples.append(couple)
    
    # Если не нашли через детей, группируем по ролям (дедушка+бабушка, отец+мать)
    if not couples:
        males = [m for m in gen_members if get_gender_order(m.get('role', 'OTHER')) == 1]
        females = [m for m in gen_members if get_gender_order(m.get('role', 'OTHER')) == 2]
        
        # Создаём пары по порядку
        for i, male in enumerate(males):
            if i < len(females):
                couples.append((male.get('id'), females[i].get('id')))
    
    return couples


def get_gender_order(role):
    """Возвращает порядок: 1 - мужской, 2 - женский, 3 - другое"""
    male_roles = {'GRANDFATHER', 'FATHER', 'SON', 'BROTHER', 'UNCLE', 'NEPHEW', 'GRANDSON'}
    female_roles = {'GRANDMOTHER', 'MOTHER', 'DAUGHTER', 'SISTER', 'AUNT', 'NIECE', 'GRANDDAUGHTER'}
    
    if role in male_roles:
        return 1
    elif role in female_roles:
        return 2
    return 3


if __name__ == '__main__':
    logger.info("PDF Server v2 запущен на порту 5001")
    app.run(host='0.0.0.0', port=5001, debug=True)
