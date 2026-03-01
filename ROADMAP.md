# Blockchain Roadmap

## Текущее состояние

### Реализовано
- [x] AccountService — управление аккаунтами (create, update roles, deactivate)
- [x] BlockChainService — storeData, getPendingTransactions
- [x] BlockCreationService — создание блока с проверкой подписи валидатора
- [x] TransactionApplierService — применение транзакций при создании блока
- [x] PendingTransactionRepository — хранение pending-транзакций в LevelDB
- [x] ValidatorSelectionService — round-robin выбор валидатора
- [x] AccountRepository, BlockRepository — LevelDB
- [x] CryptoService — подпись, проверка, merkle root, адреса

---

## Фаза 1: Bootstrap и Genesis

### 1.1 Genesis Block
- [ ] Определить детерминированный genesis (height=0, previousHash="0")
- [ ] BlockchainBootstrap / ApplicationRunner при старте
- [ ] Проверка: если блоков нет — создать genesis
- [ ] Включить CREATE_ACCOUNT для admin в genesis (опционально)

### 1.2 Admin Bootstrap
- [ ] Конфиг: `ADMIN_PUBLIC_KEY` (env или application.yml)
- [ ] При старте: если admin нет — создать аккаунт с ролью ADMIN
- [ ] Альтернатива: admin только через транзакцию в genesis

---

## Фаза 2: P2P и синхронизация

### 2.1 Хранение пиров
- [ ] PeerRepository (LevelDB): ключ `peers`, список адресов нод
- [ ] Конфиг: `bootstrap.peers` — начальный список пиров
- [ ] API: GET /peers, управление списком

### 2.2 Добавление/удаление пиров
- [ ] TransactionType: ADD_PEER, REMOVE_PEER
- [ ] DTO: AddPeerPayload, RemovePeerPayload
- [ ] Обработка в TransactionApplierService
- [ ] Право на добавление: только ADMIN

### 2.3 Синхронизация блоков
- [ ] Запрос блоков у пиров (начиная с height или hash)
- [ ] Получение pending-транзакций от пиров
- [ ] Периодический sync (scheduler) или по событию
- [ ] Retry при недоступности пира

### 2.4 Распространение
- [ ] При создании блока — отправить новым пирам
- [ ] При получении pending-транзакции — ретрансляция пирам
- [ ] WebSocket / HTTP polling / очередь сообщений

---

## Фаза 3: Разрешение форков

### 3.1 Хранение блоков
- [ ] Индекс по height (блоки на одной высоте для обнаружения форка)
- [ ] Поддержка нескольких кандидатов на одну высоту
- [ ] BlockRepository: findByHeight, findAlternatives

### 3.2 Longest Chain Rule
- [ ] При получении блока — сравнить длину цепочек
- [ ] Выбор канонической цепочки (самая длинная валидная)
- [ ] Сравнение своей цепочки с цепочками пиров

### 3.3 Reorg (переключение цепочки)
- [ ] Откат состояния: отменить применение транзакций из старой ветки
- [ ] Применение транзакций из новой ветки
- [ ] Логика rollback в TransactionApplierService (обратные операции)
- [ ] Pending: вернуть откатанные транзакции в pool

### 3.4 Детекция форка
- [ ] При получении блока: проверка previousHash
- [ ] Если previousHash не совпадает — альтернативная ветка
- [ ] Запрос полной цепочки у пира для сравнения

---

## Фаза 4: Улучшения и надёжность

### 4.1 Детерминизм
- [ ] Единый порядок транзакций в блоке (sort by id / timestamp)
- [ ] Синхронизация времени (NTP) для валидаторов
- [ ] Лимит транзакций в блоке (config)

### 4.2 Валидация
- [ ] Проверка блока перед применением (подпись, merkle, транзакции)
- [ ] Проверка транзакций перед добавлением в pending (лимиты, дубли)
- [ ] Rate limiting на API

### 4.3 Мониторинг
- [ ] Логирование создания блоков, sync, reorg
- [ ] Метрики: height, pending count, peers count
- [ ] Health endpoint: /health (связь с пирами, последний блок)

### 4.4 Безопасность
- [ ] Проверка whitelist пиров (опционально)
- [ ] TLS для P2P соединений
- [ ] Валидация входных данных (payload, адреса)

---

## Фаза 5: Дополнительно (по необходимости)

- [ ] Persistence для pending при рестарте (уже в LevelDB)
- [ ] API получения блоков по диапазону height (для sync)
- [ ] Snapshot состояния для быстрого присоединения новой ноды
- [ ] Конфигурируемые лимиты (размер блока, размер payload)

---

## Порядок реализации

```
1. Bootstrap (genesis + admin)     ← база для запуска
2. Peer storage + ADD_PEER        ← чтобы ноды знали друг о друге
3. Block sync между пирами        ← главная механика P2P
4. Fork resolution + reorg        ← консистентность при конфликтах
5. Улучшения и надёжность
```

---

## Зависимости между задачами

```
Bootstrap ─────────────────────────────┐
                                       │
ADD_PEER / PeerRepository ─────────────┼──→ Block Sync ──→ Fork Resolution
                                       │
Genesis (deterministic) ───────────────┘
```
