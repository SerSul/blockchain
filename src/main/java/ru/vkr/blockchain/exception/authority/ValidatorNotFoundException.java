package ru.vkr.blockchain.exception.authority;

import ru.vkr.blockchain.exception.BlockchainException;

public class ValidatorNotFoundException extends BlockchainException {
    public ValidatorNotFoundException(String address) {
        super("Validator not found: " + address);
    }
}
