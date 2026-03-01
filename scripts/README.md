# Скрипты для подписей и запросов API

На **Windows (PowerShell)** всегда вызывайте скрипты через `python`:

```powershell
# Из папки scripts:
python .\build_peer_request.py -k admin.pem --creator-public-key "MFkw..." --peer-url "http://localhost:8081" --action add
python .\build_create_account_request.py -k admin.pem --public-key "MFkw..."
python .\sign_payload.py -k admin.pem -p "текст для подписи"
```

Из **корня проекта** (`blockchain`):

```powershell
python scripts/build_peer_request.py -k scripts/admin.pem --creator-public-key "..." --peer-url "http://localhost:8081" --action add
```

## Зависимости

```powershell
pip install -r requirements.txt
```

## Скрипты

| Скрипт | Назначение |
|--------|------------|
| **sign_payload.py** | Подписать произвольную строку (payload) → Base64 подпись |
| **build_create_account_request.py** | Полный JSON для POST /api/accounts (CREATE_ACCOUNT) |
| **build_peer_request.py** | Полный JSON для ADD_PEER или REMOVE_PEER |
