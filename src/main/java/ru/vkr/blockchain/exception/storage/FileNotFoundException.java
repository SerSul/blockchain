package ru.vkr.blockchain.exception.storage;

import ru.vkr.blockchain.exception.BlockchainException;

public class FileNotFoundException extends BlockchainException {

    public FileNotFoundException(String fileHash) {
        super("File not found: " + fileHash);
    }
}
