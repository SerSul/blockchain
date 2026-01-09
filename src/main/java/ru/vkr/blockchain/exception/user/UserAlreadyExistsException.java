package ru.vkr.blockchain.exception.user;

import ru.vkr.blockchain.exception.BlockchainException;

public class UserAlreadyExistsException extends BlockchainException {
    public UserAlreadyExistsException(String username) {
        super("User already exists: " + username);
    }
}
