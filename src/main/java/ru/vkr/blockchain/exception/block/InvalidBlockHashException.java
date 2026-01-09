package ru.vkr.blockchain.exception.block;

import ru.vkr.blockchain.exception.BlockchainException;

public class InvalidBlockHashException extends BlockchainException {
    public InvalidBlockHashException(String expected, String actual) {
        super(String.format("Invalid block hash. Expected: %s, Actual: %s", expected, actual));
    }
}
