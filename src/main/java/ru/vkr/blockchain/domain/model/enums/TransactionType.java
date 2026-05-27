package ru.vkr.blockchain.domain.model.enums;

public enum TransactionType {
    CREATE_ACCOUNT,           // Создание нового аккаунта
    UPDATE_ACCOUNT_ROLES,     // Изменение ролей (в т.ч. назначение VALIDATOR)
    DEACTIVATE_ACCOUNT,       // Деактивация аккаунта
    STORE_DATA,               // Сохранение данных
    STORE_FILE,               // Сохранение файла (метаданные в блокчейне, содержимое в MinIO)
    ADD_PEER,                 // Добавление peer-ноды в список
    REMOVE_PEER               // Удаление peer-ноды из списка
}
