# Скрипты для подписей и запросов API

На **Windows (PowerShell)** всегда вызывайте скрипты через `python`:

```powershell
# Из папки scripts:
python .\build_create_account_request.py -k admin.pem --public-key "MFkw..."
python .\sign_payload.py -k admin.pem -p "текст для подписи"
```

Из **корня проекта** (`blockchain`):

```powershell
python scripts/build_create_account_request.py -k scripts/admin.pem --public-key "..."
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
