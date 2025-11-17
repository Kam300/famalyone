# 🐛 Исправление: Краш Приложения

## Проблема
```
android.view.InflateException: You must supply a layout_width attribute.
```

Приложение крашилось при открытии диалога из-за неправильного стиля `CustomAlertDialog`.

## Причина
Стиль `CustomAlertDialog` использовал атрибуты `backgroundInset*`, которые не поддерживаются в Material3 и вызывали ошибку инфляции layout.

## Решение

### Убраны Проблемные Атрибуты
Удалены:
- `alertDialogStyle`
- `CustomAlertDialog` стиль
- `materialAlertDialogBodyTextStyle`
- `DialogBodyText` стиль

### Оставлены Рабочие Атрибуты
```xml
<style name="ModernDialog">
    <item name="colorSurface">@color/dialog_background_purple</item>
    <item name="shapeAppearanceMediumComponent">@style/DialogShapeAppearance</item>
    <item name="shapeAppearanceLargeComponent">@style/DialogShapeAppearance</item>
</style>

<style name="DialogShapeAppearance">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">28dp</item>
</style>
```

## Результат

✅ Приложение не крашится
✅ Диалоги открываются
✅ Светло-фиолетовый фон работает
✅ Скругленные углы применяются через ShapeAppearance

## Тестирование

Проверьте:
1. Настройки → Выбор темы ✅
2. О приложении → Проверка обновлений ✅
3. Список → Удаление ✅

---

**Краш исправлен!** ✅
