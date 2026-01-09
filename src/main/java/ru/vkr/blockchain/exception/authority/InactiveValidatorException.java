package ru.vkr.blockchain.exception.authority;

import ru.vkr.blockchain.exception.BlockchainException;

public class InactiveValidatorException extends BlockchainException {
    public InactiveValidatorException(String address) {
        super("Validator is inactive: " + address);
    }
}
