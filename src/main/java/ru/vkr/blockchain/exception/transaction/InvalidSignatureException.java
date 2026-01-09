package ru.vkr.blockchain.exception.transaction;

import ru.vkr.blockchain.exception.BlockchainException;

public class InvalidSignatureException extends BlockchainException {
    public InvalidSignatureException(String entityType, String id) {
        super(String.format("Invalid signature for %s: %s", entityType, id));
    }
}
