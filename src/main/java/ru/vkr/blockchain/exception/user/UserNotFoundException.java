package ru.vkr.blockchain.exception.user;

import ru.vkr.blockchain.exception.BlockchainException;

public class UserNotFoundException extends BlockchainException {
    public UserNotFoundException(String identifier) {
        super("User not found: " + identifier);
    }
}
