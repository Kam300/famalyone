# Обновление иконки приложения

## Что было сделано:

### 1. Создана новая адаптивная иконка
**Файлы:**
- `app/src/main/res/drawable/ic_launcher_foreground_icon.xml` - векторная иконка
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_icon.xml` - адаптивная иконка
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round_icon.xml` - круглая адаптивная иконка

### 2. Добавлены статические версии
Скопированы из `icon.png` в папки:
- `mipmap-mdpi/ic_launcher_icon.png` (48x48)
- `mipmap-hdpi/ic_launcher_icon.png` (72x72)
- `mipmap-xhdpi/ic_launcher_icon.png` (96x96)
- `mipmap-xxhdpi/ic_launcher_icon.png` (144x144)
- `mipmap-xxxhdpi/ic_launcher_icon.png` (192x192)

И круглые версии:
- `mipmap-*/ic_launcher_round_icon.png`

### 3. Обновлен AndroidManifest.xml
```xml
android:icon="@mipmap/ic_launcher_icon"
android:roundIcon="@mipmap/ic_launcher_round_icon"
```

### 4. Добавлены цвета для иконки
В `colors.xml`:
```xml
<color name="ic_launcher_background_icon">#FFFFFF</color>
<color name="purple_primary">#6C63FF</color>
<color name="purple_secondary">#8B84FF</color>
<color name="purple_light">#B8B4FF</color>
```

## Дизайн новой иконки:

### Концепция: "Семейное дерево"
- **Фон:** Белый (#FFFFFF)
- **Основа:** Дерево с кроной (фиолетовый градиент)
- **Элементы:** 
  - Центральная фигура (главный родитель)
  - Два родителя сверху
  - Два ребенка снизу
  - Соединительные линии
  - Сердечко в центре (оранжевый акцент)

### Цветовая схема:
- **Крона дерева:** `#8B84FF` (фиолетовый вторичный)
- **Ствол:** `#6C63FF` (фиолетовый основной)
- **Фигуры:** Белые круги
- **Линии:** Белые
- **Сердце:** `#FF6B35` (оранжевый акцент)

## Как это работает:

### Android 8.0+ (API 26+)
Использует адаптивные иконки:
- Автоматически адаптируется к форме устройства
- Поддерживает анимации
- Монохромная версия для тематических иконок

### Android 7.1 и ниже
Использует статические PNG файлы из папок mipmap-*

## Варианты иконок:

### Вариант 1: Ваш PNG с фоном (АКТИВНЫЙ)
- Использует ваш `icon.png` в центре
- Фон: радиальный градиент (фиолетовый)
- Файл: `ic_launcher_png.xml`

### Вариант 2: Векторное семейное дерево
- Полностью векторная иконка
- Дизайн: семейное дерево с фигурами
- Файл: `ic_launcher_icon.xml`

### Вариант 3: Простая рамка
- Ваш PNG в белом круге с цветной рамкой
- Файл: `ic_launcher_simple.xml`

## Результат:
✅ Используется ваш оригинальный icon.png
✅ Красивый фоновый градиент
✅ Адаптивная иконка для современных устройств
✅ Статические версии для старых устройств
✅ Соответствие цветовой схеме приложения

## Тестирование:
1. Пересоберите приложение
2. Установите на устройство
3. Проверьте иконку на рабочем столе
4. Проверьте в списке приложений
5. На Android 8.0+ попробуйте долгое нажатие для анимации

## Переключение между вариантами:

### Чтобы использовать векторную иконку:
```xml
android:icon="@mipmap/ic_launcher_icon"
android:roundIcon="@mipmap/ic_launcher_round_icon"
```

### Чтобы использовать ваш PNG (текущий):
```xml
android:icon="@mipmap/ic_launcher_png"
android:roundIcon="@mipmap/ic_launcher_png"
```

## Если нужно изменить:
1. **Изменить фон:** Отредактируйте цвета в `ic_launcher_foreground_png.xml`
2. **Заменить PNG:** Замените `icon.png` в папке drawable
3. **Размер PNG:** Измените width/height в `ic_launcher_foreground_png.xml`
4. **Цвета:** Измените в `colors.xml`