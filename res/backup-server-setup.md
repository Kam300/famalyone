# Серверный backup + Google OAuth (Android + будущий Web)

Этот документ описывает рабочую схему backup, которая уже внедрена в проект:

- Android получает `Google ID token`.
- Backend верифицирует токен и определяет владельца по `sub`.
- Для каждого пользователя хранится один актуальный архив backup.
- Restore делает `merge + dedup`.
- После restore выполняется авто-синк лиц в AI.

## 1. Что уже реализовано

### Backend (`vue_project/backend/telegram_service.py`)

- `POST /api/backup/upload` (`/backup/upload`)
- `GET /api/backup/meta` (`/backup/meta`)
- `GET /api/backup/download` (`/backup/download`)
- `DELETE /api/backup` (`/backup`)
- Верификация Google ID token через `google.oauth2.id_token`.
- Лимиты:
  - `BACKUP_MAX_FILE_MB` (по умолчанию 250 MB)
  - `BACKUP_MAX_UNCOMPRESSED_MB` (по умолчанию 700 MB)
- Хранение:
  - `backup_storage/<hashed_owner_sub>/latest.zip`
  - `backup_storage/<hashed_owner_sub>/latest.meta.json`

### Android (`famalyone`)

- `BackupActivity` переведена на server backup API.
- `GoogleAuthManager` для sign-in и получения ID token.
- `BackupApi` (upload/meta/download/delete).
- `BackupArchiveManager`:
  - формирование zip-архива,
  - restore merge+dedup.
- `FaceSyncManager`:
  - авто-перерегистрация фото в AI после restore.

## 2. Настройка Google OAuth

Важно: для Android sign-in и серверной проверки токена используются разные OAuth client IDs, но в одном Google Cloud проекте.

- Android OAuth client: для подписи Android-приложения (package + SHA-1).
- Web OAuth client: именно его ID используется в `requestIdToken(...)` и на backend для проверки `aud`.

### Шаг 1. Создать/настроить OAuth consent screen

В Google Cloud Console:

1. Откройте `APIs & Services -> OAuth consent screen`.
2. Создайте экран согласия (External/Internal).
3. Заполните минимум обязательные поля.
4. Для теста добавьте себя в Test users.

### Шаг 2. Создать Android OAuth client

`APIs & Services -> Credentials -> Create credentials -> OAuth client ID -> Android`

Поля:

- Package name: `com.example.familyone`
- SHA-1: отпечаток debug/release keystore.

Рекомендуется создать минимум 2 Android-клиента:

- Debug (для разработки)
- Release (для подписанной сборки)

### Шаг 3. Создать Web OAuth client

`APIs & Services -> Credentials -> Create credentials -> OAuth client ID -> Web application`

Для текущего Android flow можно оставить:

- Authorized JavaScript origins: пусто
- Authorized redirect URIs: пусто

Скопируйте `Client ID` вида:
`123...abc.apps.googleusercontent.com`

### Шаг 4. Подставить Web client ID в проект

1. Android:
   - файл: `famalyone/app/src/main/res/values/backup_auth_strings.xml`
   - строка: `google_web_client_id`

2. Backend:
   - файл: `vue_project/backend/.env`
   - переменная: `GOOGLE_OAUTH_WEB_CLIENT_ID`

Значение в обоих местах должно быть одинаковым (Web client ID).

## 3. Как получить SHA-1

### Debug SHA-1 (Windows, Android Studio JBR)

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe' -list -v `
  -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android -keypass android
```

Ищите строку `SHA1: ...`.

### Release SHA-1

Выполните `keytool -list -v` для вашего release keystore с вашими alias/password.

## 4. Backend env (обязательное для backup)

В `vue_project/backend/.env`:

```env
GOOGLE_OAUTH_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
BACKUP_STORAGE_DIR=backup_storage
BACKUP_MAX_FILE_MB=250
BACKUP_MAX_UNCOMPRESSED_MB=700
BACKUP_SCHEMA_VERSION=1
```

Опционально для CORS (под будущий web):

```env
CORS_ORIGINS=https://your-web-domain.com,https://staging.your-web-domain.com
```

## 5. Быстрая проверка после настройки

1. Запустите backend (например через `vue_project/scripts/deploy-and-run.ps1`).
2. Проверьте health:
   - `GET /api/health`
   - ожидается `"backup": true`
3. В Android:
   - откройте экран backup,
   - выполните вход через Google,
   - нажмите обновление статуса.

Если все настроено, ошибки `ApiException: 10` быть не должно.

## 6. Формат backup архива

`zip` содержит:

- `manifest.json`
- `members.json`
- `member_photos.json`
- `assets/<sha256>.jpg`

`manifest.json`:

- `schemaVersion`
- `createdAtUtc`
- `appVersion`
- `compression`
- `counts`
- `checksumSha256`

## 7. Логика restore

1. Проверка schema/checksum.
2. Индекс локальных участников по fingerprint:
   - `lower(lastName|firstName|patronymic)|birthDate|role`.
3. Merge:
   - найден match -> дозаполняются пустые поля;
   - нет match -> создается новый участник.
4. Второй проход по родительским связям.
5. Восстановление фото с dedup по hash.
6. Восстановление profile photo (если локально пусто).
7. Авто AI sync после restore.

## 8. Типовые ошибки и причины

### `ApiException: 10` (DEVELOPER_ERROR)

Обычно причина:

- в Android указан не Web client ID;
- `google_web_client_id` и `GOOGLE_OAUTH_WEB_CLIENT_ID` не совпадают;
- неверный package/SHA-1 в Android OAuth client;
- изменения в Google Console еще не распространились (подождать 5-30 минут).

### `401 Invalid or expired Google token`

- истек токен;
- backend проверяет другой `aud` (не тот Web client ID).

### `500 GOOGLE_OAUTH_WEB_CLIENT_ID is not configured`

- не заполнен `GOOGLE_OAUTH_WEB_CLIENT_ID` в `backend/.env`.

### `413 Backup exceeds max size`

- архив больше `BACKUP_MAX_FILE_MB`.

### `400 Missing required files`

- архив не содержит `manifest.json`, `members.json`, `member_photos.json`.

## 9. Совместимость с будущим Web

Текущий backend-контракт уже общий для Android и Web:

- один и тот же Bearer Google ID token;
- один и тот же owner mapping по `sub`;
- один и тот же архивный формат и `schemaVersion`.

Это позволяет добавить Web UI без изменения backend API.
