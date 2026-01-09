package ru.vkr.blockchain.exception.storage;

import ru.vkr.blockchain.exception.BlockchainException;

public class StorageException extends BlockchainException {
    public StorageException(String message) {
        super("Storage error: " + message);
    }

    public StorageException(String message, Throwable cause) {
        super("Storage error: " + message, cause);
    }
}
