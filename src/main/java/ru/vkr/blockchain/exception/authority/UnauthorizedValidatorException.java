package ru.vkr.blockchain.exception.authority;

import ru.vkr.blockchain.exception.BlockchainException;

public class UnauthorizedValidatorException extends BlockchainException {
    public UnauthorizedValidatorException(String address) {
        super("Validator is not authorized: " + address);
    }
}
