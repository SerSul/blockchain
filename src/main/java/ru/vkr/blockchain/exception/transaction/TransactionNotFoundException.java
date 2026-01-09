package ru.vkr.blockchain.exception.transaction;

import ru.vkr.blockchain.exception.BlockchainException;

public class TransactionNotFoundException extends BlockchainException {
    public TransactionNotFoundException(String id) {
        super("Transaction not found: " + id);
    }
}
