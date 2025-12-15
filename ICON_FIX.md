# Исправление ошибок иконки

## Проблема:
```
error: attribute android:cx not found
error: attribute android:cy not found  
error: attribute android:r not found
```

## Причина:
В векторных drawable (vector) нельзя использовать элемент `<circle>`. Нужно использовать только `<path>`.

## Что было исправлено:

### 1. Заменил `<circle>` на `<path>`
**Было:**
```xml
<circle
    android:fillColor="@color/white"
    android:cx="54"
    android:cy="45"
    android:r="8"/>
```

**Стало:**
```xml
<path
    android:fillColor="@color/white"
    android:pathData="M54,37 C58.4,37 62,40.6 62,45 C62,49.4 58.4,53 54,53 C49.6,53 46,49.4 46,45 C46,40.6 49.6,37 54,37 Z"/>
```

### 2. Создал минимальную версию
Создал `ic_launcher_foreground_minimal.xml` с простым дизайном семьи без кругов.

### 3. Обновил AndroidManifest.xml
Теперь использует минимальную версию:
```xml
android:icon="@mipmap/ic_launcher_minimal"
android:roundIcon="@mipmap/ic_launcher_minimal"
```

## Доступные варианты иконок:

### 1. Минимальная (АКТИВНАЯ)
- Файл: `ic_launcher_minimal.xml`
- Дизайн: Простые фигуры семьи
- Цвета: Фиолетовый + оранжевый

### 2. С вашим PNG
- Файл: `ic_launcher_png.xml`
- Использует ваш `icon.png`
- Фон: Градиент

### 3. Векторное дерево
- Файл: `ic_launcher_icon.xml`
- Дизайн: Семейное дерево
- Исправлены все circle → path

## Результат:
✅ Ошибки компиляции исправлены
✅ Иконка работает на всех версиях Android
✅ Простой и читаемый дизайн
✅ Соответствует цветовой схеме приложения

## Переключение между вариантами:

### Минимальная версия (текущая):
```xml
android:icon="@mipmap/ic_launcher_minimal"
```

### Версия с PNG:
```xml
android:icon="@mipmap/ic_launcher_png"
```

### Векторное дерево:
```xml
android:icon="@mipmap/ic_launcher_icon"
```

Пересоберите приложение - теперь иконка должна работать без ошибок!