# Postman: API блокчейна

## Коллекции

| Файл | Описание |
|------|----------|
| **Blockchain API.postman_collection.json** | Всё API: аккаунты, блоки, транзакции |

## Окружение

**Blockchain Local.postman_environment.json** — переменные для локального запуска: `baseUrl`, `creator_public_key`, `validator_public_key`. Импортируйте и выберите это окружение в Postman.

## Импорт

1. Postman → Import → выберите нужные файлы (коллекции и окружение).
2. Выберите окружение **Blockchain Local** в выпадающем списке (или задайте переменные в коллекции).

## Подписи

Для транзакций используйте скрипты в `scripts/`: **sign_payload.py**, **build_create_account_request.py** (см. `scripts/README.md`).
