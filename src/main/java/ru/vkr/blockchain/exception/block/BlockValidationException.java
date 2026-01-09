package ru.vkr.blockchain.exception.block;

import ru.vkr.blockchain.exception.BlockchainException;

public class BlockValidationException extends BlockchainException {
    public BlockValidationException(String message) {
        super("Block validation failed: " + message);
    }

    public BlockValidationException(String hash, String reason) {
        super(String.format("Block %s validation failed: %s", hash, reason));
    }
}
