# Docker: одна или две ноды

Используется один `docker-compose.yml` в корне репозитория. Две ноды — это **два независимых стека** с разными env-файлами и именем проекта (`COMPOSE_PROJECT_NAME`), чтобы не конфликтовали порты и volumes.

## Подготовка ключей (только для ноды 1)

```bash
pip install -r scripts/requirements.txt
python scripts/generate_keys.py --out scripts/keys --name validator1
python scripts/generate_keys.py --out scripts/keys --name validator2
```

Публичные ключи из `scripts/keys/validator1_public.b64` и `validator2_public.b64` вставьте в `docker/env.node1.example` → сохраните как `.env.node1`.

## Нода 1 (genesis, порт 8080)

```powershell
cd C:\Users\Dima\IdeaProjects\blockchain
copy docker\env.node1.example .env.node1
# отредактируйте BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS_*

docker compose --env-file .env.node1 up -d --build
```

Проверка:

- API: http://localhost:8080/api/network/status
- Explorer: http://localhost:8080/explorer/blocks

Дождитесь старта (genesis создаётся при первом запуске с пустым LevelDB).

У **ноды 1** в `.env.node1` уже указан пир на ноду 2 (`http://host.docker.internal:8081`) и `SYNC_ENABLED=true`.  
Genesis и ключи валидаторов нужны только здесь; пир на вторую ноду нужен, чтобы **подтягивать её блоки и pending-транзакции**, когда node2 появится в сети.

## Нода 2 (порт 8081, без ключей валидаторов)

Нода 2 **не обязана** задавать `BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS_*`. При включённом sync она:

1. Подтянет блоки с ноды 1
2. Импортирует аккаунты и списки валидаторов через `GET /api/network/join-snapshot`

```powershell
copy docker\env.node2.example .env.node2
# peer уже указан: http://host.docker.internal:8080

docker compose --env-file .env.node2 up -d --build
```

Проверка:

- http://localhost:8081/api/network/status — высота должна совпасть с нодой 1
- http://localhost:8081/explorer/validators

**Важно:** сначала поднимите ноду 1, затем ноду 2. У ноды 2 свои Postgres, MinIO и LevelDB (отдельные volumes за счёт `COMPOSE_PROJECT_NAME=bc-node2`).

### Связь пиров (обе стороны)

| Нода | Порт | `BLOCKCHAIN_BOOTSTRAP_PEERS_0` |
|------|------|--------------------------------|
| 1 | 8080 | `http://host.docker.internal:8081` |
| 2 | 8081 | `http://host.docker.internal:8080` |

Обе ноды с `BLOCKCHAIN_BOOTSTRAP_SYNC_ENABLED=true`. Нода 1 может стартовать одна: пир в списке появится сразу, синхронизация с 8081 заработает после `docker compose ... .env.node2 up`.

## Остановка

```powershell
docker compose --env-file .env.node1 down
docker compose --env-file .env.node2 down
```

Полная очистка данных:

```powershell
docker compose --env-file .env.node1 down -v
docker compose --env-file .env.node2 down -v
```

## Одна нода (без второй)

```powershell
copy .env.example .env
# заполните BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS_0

docker compose --env-file .env up -d --build
```

## Переменные

| Переменная | Нода 1 | Нода 2 |
|------------|--------|--------|
| `APP_PORT` | 8080 | 8081 |
| `DB_HOST_PORT` | 5433 | 5434 |
| `MINIO_API_PORT` | 9000 | 9002 |
| `BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS_*` | обязательны для genesis | можно пусто |
| `BLOCKCHAIN_BOOTSTRAP_PEERS_0` | URL ноды 2 (`:8081`) | URL ноды 1 (`:8080`) |
| `BLOCKCHAIN_BOOTSTRAP_SYNC_ENABLED` | true | true |

## Linux

Вместо `host.docker.internal` для пира может понадобиться IP хоста, например `http://172.17.0.1:8080`, или добавьте в `docker-compose` для сервиса `node`:

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

## Подпись блоков на ноде 2

Join-snapshot переносит **публичные** ключи валидаторов. Чтобы **создавать** блоки с ноды 2, на этой машине всё равно нужен соответствующий `.pem` (см. `scripts/test_validator_flow.py`).
