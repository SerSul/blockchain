package ru.vkr.blockchain.domain.model.enums;

public enum AccountRole {
    USER,        // Обычный пользователь - создаёт транзакции
    VALIDATOR,   // Валидатор - создаёт и подписывает блоки
    AUDITOR,     // Аудитор - только чтение (read-only)
    ADMIN        // Администратор - полный доступ
}