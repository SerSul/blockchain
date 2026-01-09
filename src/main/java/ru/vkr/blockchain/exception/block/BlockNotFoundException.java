package ru.vkr.blockchain.exception.block;

import ru.vkr.blockchain.exception.BlockchainException;

public class BlockNotFoundException extends BlockchainException {
    public BlockNotFoundException(String hash) {
        super("Block not found: " + hash);
    }

    public BlockNotFoundException(Integer height) {
        super("Block not found at height: " + height);
    }
}
