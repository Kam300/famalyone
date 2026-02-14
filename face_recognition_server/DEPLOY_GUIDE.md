# 🚀 Инструкция: Запуск сервера на новом ПК

## Что копировать на новый ПК

Скопируйте папку `face_recognition_server/` целиком:
```
face_recognition_server/
├── server.py
├── requirements.txt
├── DEPLOY_GUIDE.md  (этот файл)
└── (опционально) face_encodings.json, reference_photos/
```

---

## Шаг 1: Установка Python 3.11+

### Windows
Скачайте с https://www.python.org/downloads/  
⚠️ При установке **обязательно** поставьте галочку **"Add Python to PATH"**

### Linux (Ubuntu/Debian)
```bash
sudo apt update && sudo apt install python3 python3-pip python3-venv
```

Проверка:
```bash
python --version   # или py --version на Windows
```

---

## Шаг 2: Установка зависимостей

```bash
cd face_recognition_server

# Windows — используем dlib-bin (не требует компилятора C++)
pip install cmake dlib-bin
pip install face_recognition --no-deps
pip install face_recognition_models
pip install flask==3.0.0 flask-cors==4.0.0 numpy==1.24.3 Pillow==10.1.0 reportlab==4.0.7 waitress==2.1.2

# Linux — dlib собирается из исходников (нужен компилятор)
sudo apt install build-essential cmake libboost-all-dev
pip install -r requirements.txt
```

Проверка:
```bash
python -c "import dlib; import face_recognition; import flask; print('OK')"
```

---

## Шаг 3: Запуск сервера

```bash
python server.py
```

Сервер запустится на `http://localhost:5000`. Проверка:
```bash
curl http://localhost:5000/health
```

---

## Шаг 4: Cloudflare Tunnel (чтобы сервер был доступен из интернета)

### 4.1 Установка cloudflared

**Windows:**
```powershell
winget install Cloudflare.cloudflared
```

**Linux:**
```bash
curl -L https://pkg.cloudflare.com/cloudflared-linux-amd64.deb -o cloudflared.deb
sudo dpkg -i cloudflared.deb
```

### 4.2 Авторизация

```bash
cloudflared tunnel login
```
Откроется браузер → выберите домен `indevs.in` → авторизуйтесь.

### 4.3 Создание туннеля

```bash
cloudflared tunnel create familyone-api
```
Запишите **TUNNEL_ID** из вывода (вида `7fea8073-5224-...`).

### 4.4 DNS запись

```bash
cloudflared tunnel route dns familyone-api totalcode.indevs.in
```

Если команда выдаёт ошибку — создайте CNAME вручную в [Cloudflare Dashboard](https://dash.cloudflare.com):

| Type | Name | Target | Proxy |
|------|------|--------|-------|
| CNAME | totalcode | `<TUNNEL_ID>.cfargotunnel.com` | Proxied ☁️ |

> ⚠️ Если на старом ПК остался туннель — сначала удалите старую DNS запись.

### 4.5 Конфиг

Создайте файл:  
- **Windows:** `C:\Users\<ИМЯ>\.cloudflared\config.yml`  
- **Linux:** `~/.cloudflared/config.yml`

```yaml
tunnel: <TUNNEL_ID>
credentials-file: <ПУТЬ_К>/<TUNNEL_ID>.json

ingress:
  - hostname: totalcode.indevs.in
    service: http://localhost:5000
    originRequest:
      connectTimeout: 300s
      noTLSVerify: true
      httpHostHeader: localhost
      keepAliveConnections: 100
      keepAliveTimeout: 90s
  - service: http_status:404
```

### 4.6 Запуск туннеля

```bash
cloudflared tunnel run familyone-api
```

Проверка: откройте `https://totalcode.indevs.in/health`

---

## Автозапуск (опционально)

### Windows — как служба
```powershell
cloudflared service install
```

### Linux — systemd
```bash
sudo cloudflared service install
sudo systemctl enable cloudflared
sudo systemctl start cloudflared
```

Для сервера Python на Linux создайте `/etc/systemd/system/familyone.service`:
```ini
[Unit]
Description=FamilyOne API Server
After=network.target

[Service]
WorkingDirectory=/opt/face_recognition_server
ExecStart=/usr/bin/python3 server.py
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable familyone && sudo systemctl start familyone
```

---

## Краткая шпаргалка

```bash
# Каждый раз при запуске ПК (если нет автозапуска):
python server.py                          # терминал 1
cloudflared tunnel run familyone-api      # терминал 2
```
