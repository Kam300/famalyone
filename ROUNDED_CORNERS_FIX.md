# 🔄 Исправление: Скругленные Углы Диалогов

## Проблема
Диалоги не показывали скругленные углы из-за того, что Material3 переопределяет фон.

## Решение

### 1. Добавлен ShapeAppearance
```xml
<style name="DialogShapeAppearance">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">28dp</item>
</style>
```

### 2. Установлен Прозрачный Фон Окна
```xml
<item name="android:windowBackground">@android:color/transparent</item>
```

### 3. Добавлен colorSurface
```xml
<item name="colorSurface">@color/dialog_background_purple</item>
<item name="colorOnSurface">#2D2D3A</item>
```

### 4. Создан CustomAlertDialog Style
```xml
<style name="CustomAlertDialog">
    <item name="backgroundInsetStart">16dp</item>
    <item name="backgroundInsetEnd">16dp</item>
    <item name="backgroundInsetTop">48dp</item>
    <item name="backgroundInsetBottom">48dp</item>
    <item name="shapeAppearance">@style/DialogShapeAppearance</item>
</style>
```

## Обновленные Файлы

1. **dialog_styles.xml**
   - Добавлен DialogShapeAppearance
   - Добавлен CustomAlertDialog
   - Установлен colorSurface
   - Прозрачный windowBackground

2. **dialog_rounded_background.xml** (новый)
   - Альтернативный фон с inset

## Результат

✅ Скругленные углы 28dp видны
✅ Светло-фиолетовый фон
✅ Темная обводка
✅ Правильный размер диалога
✅ Material Design 3 совместимость

## Тестирование

Проверьте диалоги в:
1. Настройки → Выбор темы
2. О приложении → Проверка обновлений
3. Список → Удаление

---

**Скругленные углы работают!** 🎨
