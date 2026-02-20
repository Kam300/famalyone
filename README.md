# Семейное Древо - Android приложение

Мобильное приложение для создания и управления семейным деревом на Android.

## Описание

Семейное Древо - это современное Android приложение, которое позволяет пользователям создавать, редактировать и визуализировать свое семейное дерево. Приложение поддерживает добавление фотографий, хранение информации о членах семьи и экспорт данных в различных форматах.

## Основные возможности

### ✨ Функционал

- **Добавление членов семьи** - полная информация (ФИО, пол, дата рождения, роль, фото, телефон)
- **Просмотр списка** - удобный список всех членов семьи с возможностью редактирования и удаления
- **Визуализация древа** - красивое отображение семейного дерева по ролям и иерархии
- **Экспорт данных** - сохранение данных в форматах JSON, CSV и текстовом формате
- **Резервное копирование** - локальный backup (JSON) и серверный backup через Google OAuth
- **Настройки темы** - выбор между светлой, темной и системной темой
- **Связь с членами семьи** - возможность связаться через Telegram, WhatsApp или позвонить (если указан номер телефона)
- **О приложении** - информация о приложении, проверка обновлений, ссылки

### 🎨 Дизайн

- Современный Material Design 3
- Закругленные кнопки и карточки
- Оранжевая основная тема с фиолетовыми акцентами
- Полная поддержка темной темы
- Адаптивный дизайн для различных размеров экранов

## Технологии

### Архитектура и библиотеки

- **Язык**: Kotlin
- **UI**: XML Layouts, ViewBinding
- **Архитектура**: MVVM (Model-View-ViewModel)
- **База данных**: Room Database
- **Многопоточность**: Kotlin Coroutines
- **Изображения**: Glide
- **Навигация**: Intent-based navigation
- **Темы**: AppCompat Day/Night themes
- **Сериализация**: Gson

### Зависимости

```gradle
// Core
implementation "androidx.core:core-ktx:1.17.0"
implementation "androidx.appcompat:appcompat:1.7.1"
implementation "com.google.android.material:material:1.13.0"

// Room Database
implementation "androidx.room:room-runtime:2.6.1"
implementation "androidx.room:room-ktx:2.6.1"
ksp "androidx.room:room-compiler:2.6.1"

// Lifecycle
implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7"
implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.8.7"

// Coroutines
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0"

// Glide
implementation "com.github.bumptech.glide:glide:4.16.0"
ksp "com.github.bumptech.glide:compiler:4.16.0"

// Gson
implementation "com.google.code.gson:gson:2.11.0"
```

## Структура проекта

```
app/
├── src/main/
│   ├── java/com/example/familyone/
│   │   ├── data/                    # Модели данных и база данных
│   │   │   ├── FamilyMember.kt     # Модель члена семьи
│   │   │   ├── FamilyMemberDao.kt  # DAO для доступа к БД
│   │   │   ├── FamilyDatabase.kt   # Конфигурация Room DB
│   │   │   ├── FamilyRepository.kt # Репозиторий
│   │   │   └── Converters.kt       # TypeConverters для Room
│   │   ├── ui/                      # UI компоненты
│   │   │   ├── AddMemberActivity.kt
│   │   │   ├── MemberListActivity.kt
│   │   │   ├── MemberAdapter.kt
│   │   │   ├── FamilyTreeActivity.kt
│   │   │   ├── ExportActivity.kt
│   │   │   ├── SettingsActivity.kt
│   │   │   └── AboutActivity.kt
│   │   ├── viewmodel/
│   │   │   └── FamilyViewModel.kt   # ViewModel
│   │   ├── utils/
│   │   │   ├── ThemePreferences.kt  # Управление темами
│   │   │   └── Extensions.kt        # Расширения Kotlin
│   │   ├── MainActivity.kt          # Главный экран
│   │   └── FamilyTreeApp.kt        # Application класс
│   └── res/
│       ├── layout/                  # XML layouts
│       ├── values/                  # Ресурсы (strings, colors, themes)
│       ├── values-night/            # Темная тема
│       └── xml/                     # XML конфигурации
└── build.gradle.kts
```

## Установка и запуск

### Требования

- Android Studio Narwhal 4 Feature Drop | 2025.1.4 или выше
- Android SDK 36
- Минимальная версия Android: 7.0 (API 24)
- JDK 11

### Шаги установки

1. Клонируйте репозиторий:
```bash
git clone https://github.com/yourusername/family-tree.git
```

2. Откройте проект в Android Studio

3. Синхронизируйте Gradle:
```
File -> Sync Project with Gradle Files
```

4. Запустите приложение:
```
Run -> Run 'app'
```

## Использование

### Добавление члена семьи

1. Нажмите "Добавить Члена Семьи" на главном экране
2. Заполните обязательные поля (Имя, Фамилия, Отчество, Пол, Дата рождения, Роль)
3. Опционально: добавьте номер телефона и фото
4. Нажмите "Сохранить"

### Просмотр списка

1. Нажмите "Посмотреть Список" на главном экране
2. Просмотрите список всех членов семьи
3. Используйте кнопки "Редактировать" или "Удалить" для управления
4. При наличии номера телефона доступна кнопка "Связаться" для звонков через Telegram/WhatsApp

### Визуализация древа

1. Нажмите "Древо семьи" на главном экране
2. Просмотрите иерархическое представление семьи
3. Члены семьи сгруппированы по ролям

### Экспорт данных

1. Нажмите "Экспорт Данных" на главном экране
2. Выберите формат экспорта (JSON, CSV, или текстовый файл)
3. Файлы сохраняются в папке Documents/FamilyTree

### Настройка темы

1. Нажмите "Настройки" на главном экране
2. Выберите "Настроить тему"
3. Выберите желаемую тему (Светлая, Темная, Системная)

### Backup (серверный)

Подробный гайд по настройке Google OAuth и серверного backup:

- `res/backup-server-setup.md`
- `res/mobile-api.md`

## Роли в семье

Приложение поддерживает следующие роли:

- Дедушка / Бабушка
- Отец / Мать
- Сын / Дочь
- Внук / Внучка
- Брат / Сестра
- Дядя / Тётя
- Племянник / Племянница
- Другое

## Разрешения

Приложение запрашивает следующие разрешения:

- `READ_MEDIA_IMAGES` (Android 13+) - для выбора фотографий
- `READ_EXTERNAL_STORAGE` (Android 12 и ниже) - для выбора фотографий
- `WRITE_EXTERNAL_STORAGE` (Android 9 и ниже) - для экспорта файлов
- `INTERNET` - для проверки обновлений и открытия ссылок

## База данных

Приложение использует Room Database для локального хранения данных на устройстве.  
Дополнительно доступен серверный backup (по инициативе пользователя), который отправляет архив backup на ваш backend.

### Схема БД

**Таблица: family_members**

| Поле | Тип | Описание |
|------|-----|----------|
| id | Long | Уникальный идентификатор |
| firstName | String | Имя |
| lastName | String | Фамилия |
| patronymic | String | Отчество |
| gender | Gender | Пол (MALE/FEMALE) |
| birthDate | String | Дата рождения |
| phoneNumber | String? | Номер телефона (опционально) |
| role | FamilyRole | Роль в семье |
| photoUri | String? | URI фотографии |
| maidenName | String? | Девичья фамилия |

## Известные ограничения

- Экспорт в PDF пока реализован как текстовый файл (для полноценного PDF требуется дополнительная библиотека)
- Связи между членами семьи хранятся через роли, но не как прямые отношения (parent-child)

## Будущие улучшения

- [ ] Полноценный экспорт в PDF с изображениями
- [ ] Графическая визуализация связей между членами семьи
- [ ] Поиск по членам семьи
- [x] Серверный backup через Google OAuth
- [ ] Поддержка нескольких семейных деревьев
- [ ] Статистика и аналитика
- [ ] Импорт данных из файлов
- [ ] Полноценный Web-клиент с общим backup API

## Автор

**by Kam300**

## Версия

**1.3.0.0**

## Лицензия

Этот проект создан для образовательных целей.

## Контакты

- Telegram: [https://t.me/yourChannel](https://t.me/yourChannel)
- Website: [https://yourwebsite.com](https://yourwebsite.com)

## Благодарности

- Material Design 3 от Google
- Glide библиотека для загрузки изображений
- Room Database от Android Jetpack
- Kotlin Coroutines

