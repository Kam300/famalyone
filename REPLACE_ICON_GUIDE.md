# Как заменить иконку приложения

## Текущая ситуация:
- Файл `iconnnn.ico` не найден в проекте
- Используется `icon.png` из папки drawable
- Иконка скопирована во все папки mipmap

## Чтобы заменить иконку:

### Вариант 1: Заменить icon.png
1. Поместите вашу новую иконку в `app/src/main/res/drawable/icon.png`
2. Запустите команды:
```bash
copy "app\src\main\res\drawable\icon.png" "app\src\main\res\mipmap-mdpi\ic_launcher.png"
copy "app\src\main\res\drawable\icon.png" "app\src\main\res\mipmap-hdpi\ic_launcher.png"
copy "app\src\main\res\drawable\icon.png" "app\src\main\res\mipmap-xhdpi\ic_launcher.png"
copy "app\src\main\res\drawable\icon.png" "app\src\main\res\mipmap-xxhdpi\ic_launcher.png"
copy "app\src\main\res\drawable\icon.png" "app\src\main\res\mipmap-xxxhdpi\ic_launcher.png"
```

### Вариант 2: Добавить ICO файл
1. Поместите ваш ICO файл в `app/src/main/res/drawable/`
2. Конвертируйте ICO в PNG (ICO нельзя использовать напрямую)
3. Скопируйте PNG во все папки mipmap

### Вариант 3: Использовать онлайн конвертер
1. Конвертируйте ваш ICO в PNG онлайн
2. Сохраните как `icon.png` в drawable
3. Выполните команды из Варианта 1

## Проверка:
После замены файлов:
1. Пересоберите проект
2. Переустановите приложение
3. Проверьте иконку на рабочем столе

## Важно:
- Android не поддерживает ICO формат
- Нужны PNG файлы разных размеров
- Файлы должны называться `ic_launcher.png`
- Круглые версии: `ic_launcher_round.png`

## Текущие файлы:
✅ AndroidManifest.xml настроен правильно
✅ Все папки mipmap содержат ic_launcher.png
✅ Используется icon.png как источник

**Чтобы увидеть изменения, замените `app/src/main/res/drawable/icon.png` на вашу иконку!**