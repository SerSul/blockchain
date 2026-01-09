package ru.vkr.blockchain.exception.transaction;

import ru.vkr.blockchain.exception.BlockchainException;

public class TransactionValidationException extends BlockchainException {
    public TransactionValidationException(String message) {
        super("Transaction validation failed: " + message);
    }

    public TransactionValidationException(String txId, String reason) {
        super(String.format("Transaction %s validation failed: %s", txId, reason));
    }
}
