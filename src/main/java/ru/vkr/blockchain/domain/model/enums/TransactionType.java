package ru.vkr.blockchain.domain.model.enums;

public enum TransactionType {
    CREATE_ACCOUNT,           // Создание нового аккаунта
    UPDATE_ACCOUNT_ROLES,     // Изменение ролей аккаунта (админ → валидатор и т.д.)
    DEACTIVATE_ACCOUNT,       // Деактивация аккаунта
    STORE_DATA,               // Сохранение данных
}
