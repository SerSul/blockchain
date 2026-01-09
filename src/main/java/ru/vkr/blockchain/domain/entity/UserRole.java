package ru.vkr.blockchain.domain.entity;

public enum UserRole {
    DATA_OWNER,  // Создаёт транзакции
    ADMIN,       // Управляет валидаторами
    AUDITOR      // Читает блокчейн
}