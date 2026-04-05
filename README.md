# Семейное древо: Android-приложение

Этот репозиторий содержит Android-приложение проекта. Публичное название приложения — `Семейное древо`, внутренний package id остаётся `com.example.familyone`.

## Что умеет приложение

- список членов семьи
- карточка человека и редактирование
- дерево семьи
- экспорт и backup
- настройки, PIN и тема
- работа с фото

## Что нужно для сборки

- Android Studio
- JDK 11
- Android SDK 36
- Android 7.0+ для запуска на устройстве (`minSdk = 24`)

## Быстрый старт

### В Android Studio

1. Откройте папку `famalyone`
2. Дождитесь `Gradle Sync`
3. Запустите конфигурацию `app`

### Через командную строку

Сборка debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Установка на подключённое устройство:

```powershell
.\gradlew.bat installDebug
```

Тесты:

```powershell
.\gradlew.bat test
```

## Основные папки

- `app/` — основной Android-модуль
- `app/src/main/java/` — Kotlin-код
- `app/src/main/res/` — ресурсы и layout
- `qa-artifacts/` — артефакты ручных проверок
- `res/` — дополнительные документы и материалы

## Важное

- backup и серверные функции зависят от backend из соседнего репозитория `../vue_project`
- текущая версия Android-приложения в Gradle: `1.5.1`

