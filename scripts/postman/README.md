# Postman: API блокчейна

## Коллекции

| Файл | Описание |
|------|----------|
| **Blockchain API.postman_collection.json** | Всё API: аккаунты, блоки, транзакции, пиры |
| **Blockchain Peers.postman_collection.json** | Только пиры (ADD_PEER / REMOVE_PEER) |

## Окружение

**Blockchain Local.postman_environment.json** — переменные для локального запуска: `baseUrl`, `creator_public_key`, `validator_public_key`. Импортируйте и выберите это окружение в Postman.

## Импорт

1. Postman → Import → выберите нужные файлы (коллекции и окружение).
2. Выберите окружение **Blockchain Local** в выпадающем списке (или задайте переменные в коллекции).

## Как получить подписанное тело запроса

Транзакции ADD_PEER и REMOVE_PEER подписываются приватным ключом **админа**. Подпись ставится от строки **payload** (JSON без пробелов, например `{"peer_url":"http://localhost:8081"}`).

### Скрипт (рекомендуется)

Всегда вызывайте скрипт через **`python`**, иначе в PowerShell команда не найдётся.

**Из корня проекта** (`C:\...\blockchain`):

```powershell
# Добавить пира
python scripts/build_peer_request.py -k путь/к/admin_private.pem --creator-public-key "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQVNEX6B8O7klyIptmsKafVCsTMZLcGRkzOIfGfQbnq1XerWqTA3MzPFNhk5ULbXYn3Xl3hi7kQvzCRMRLHmN7w==" --peer-url "http://localhost:8081" --action add

# Удалить пира
python scripts/build_peer_request.py -k путь/к/admin_private.pem --creator-public-key "MFkw..." --peer-url "http://localhost:8081" --action remove
```

**Из папки scripts** (`C:\...\blockchain\scripts`):

```powershell
python .\build_peer_request.py -k admin_private.pem --creator-public-key "MFkw..." --peer-url "http://localhost:8081" --action add
```

Скрипт выведет готовый JSON. Скопируйте его в Body запроса **ADD_PEER — добавить пира** или **REMOVE_PEER — удалить пира** (raw, JSON).

### Ручная подпись

1. Payload для подписи — строка: `{"peer_url":"http://localhost:8081"}` (без пробелов).
2. Подпишите её скриптом `sign_payload.py`:
   ```bash
   python scripts/sign_payload.py -k admin.pem -p '{"peer_url":"http://localhost:8081"}'
   ```
3. В Body запроса укажите:
   - `payload`: `{"peer_url":"http://localhost:8081"}`
   - `creator_public_key`: публичный ключ админа (Base64)
   - `signature`: вывод скрипта выше
   - `transaction_type`: `ADD_PEER` или `REMOVE_PEER`

## Запросы в коллекции

| Запрос              | Метод | URL                      | Описание        |
|---------------------|-------|--------------------------|-----------------|
| Список пиров        | GET   | `/api/peers`             | Список пиров    |
| ADD_PEER            | POST  | `/api/peers`             | Добавить пира   |
| REMOVE_PEER         | POST  | `/api/peers/remove`      | Удалить пира    |

Пир реально добавляется/удаляется после того, как транзакция попадёт в блок (валидатор создаёт блок с pending-транзакциями).

---

## Полное API (коллекция Blockchain API)

| Раздел | Метод | URL | Описание |
|--------|-------|-----|----------|
| **Accounts** | POST | `/api/accounts` | CREATE_ACCOUNT |
| | PATCH | `/api/accounts/roles` | UPDATE_ACCOUNT_ROLES (ADMIN) |
| | POST | `/api/accounts/deactivate` | DEACTIVATE_ACCOUNT (ADMIN) |
| **Blocks** | GET | `/api/blocks/next-validator` | Следующий валидатор |
| | POST | `/api/blocks` | Создать блок (валидатор) |
| **Transactions** | POST | `/api/transactions/store` | STORE_DATA |
| **Peers** | GET | `/api/peers` | Список пиров |
| | POST | `/api/peers` | ADD_PEER (ADMIN) |
| | POST | `/api/peers/remove` | REMOVE_PEER (ADMIN) |

Подписи для транзакций: см. скрипты в `scripts/` (sign_payload.py, build_create_account_request.py, build_peer_request.py).
