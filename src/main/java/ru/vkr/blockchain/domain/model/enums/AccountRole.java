package ru.vkr.blockchain.domain.model.enums;

public enum AccountRole {
    USER,        // Обычный пользователь - создаёт транзакции
    VALIDATOR,   // Валидатор — создаёт блоки; начальный список из конфига, новых добавляет валидатор
    AUDITOR      // Аудитор - только чтение (read-only)
}
