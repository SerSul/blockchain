# Скрипты для узла блокчейна

## Зависимости

```bash
pip install -r scripts/requirements.txt
```

## Интерактивное CLI (все функции API)

```bash
python scripts/menu_cli.py
```

Покрывает: сеть, блоки, транзакции, аккаунты, STORE_DATA / STORE_FILE, mine block, пиры, сценарий «аккаунт → валидатор».

Настройки сохраняются в `scripts/.cli_config.json` (создаётся автоматически):

- `base_url` — по умолчанию `http://localhost:8080` (нода 2: `http://localhost:8081`)
- `validator_pems` — пути к PEM валидаторов
- `user_pem` — ключ обычного пользователя для STORE_*

Для ноды 2 в меню: пункт **u** → `http://localhost:8081`.

Быстрый smoke-тест без меню:

```bash
python scripts/run_all_tests.py --base-url http://localhost:8080
```

## Ключи валидаторов (bootstrap)

```bash
python scripts/generate_keys.py --out scripts/keys --name validator1
python scripts/generate_keys.py --out scripts/keys --name validator2
```

Содержимое `validator1_public.b64` → в `.env`:

```env
BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS_0=<validator1 public b64>
BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS_1=<validator2 public b64>
```

Приватные PEM храните локально (`scripts/keys/*_private.pem`), в git не коммитьте.

## E2E: валидаторы и роли

1. Запустите узел с чистым LevelDB и заданными bootstrap-ключами.
2. Выполните тест (PEM — любой bootstrap-валидатор; для round-robin с двумя валидаторами укажите оба):

```bash
python scripts/test_validator_flow.py ^
  --base-url http://localhost:8080 ^
  --validator-pem scripts/keys/validator1_private.pem ^
  --validator-pem scripts/keys/validator2_private.pem
```

Скрипт проверяет:

- `GET /api/network/status`, `/api/network/validators`
- `CREATE_ACCOUNT` (подпись валидатора)
- `POST /api/blocks/prepare` + `POST /api/blocks`
- `PATCH /api/accounts/roles` → назначение `VALIDATOR`
- отказ изменить роли bootstrap-валидатора

## Обозреватель (UI)

После старта приложения:

| Страница | URL |
|----------|-----|
| Блоки + сводка сети | http://localhost:8080/explorer/blocks |
| Валидаторы (round-robin) | http://localhost:8080/explorer/validators |
| Аккаунты | http://localhost:8080/explorer/accounts |
| Pending | http://localhost:8080/explorer/pending |

## API для мониторинга

- `GET /api/network/status` — высота, pending, следующий валидатор
- `GET /api/network/validators` — список валидаторов (bootstrap / добавленные)
- `GET /api/blocks/next-validator` — аккаунт текущего валидатора
