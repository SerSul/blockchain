package ru.vkr.blockchain.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.service.LevelDBService;

import java.io.IOException;
import java.util.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BlockRepository {

    private final LevelDBService levelDBService;
    private static final String BLOCK_PREFIX = "block:";
    private static final String LATEST_BLOCK_KEY = "latest_block";

    public void save(Block block) throws IOException {
        String key = BLOCK_PREFIX + block.getCurrentHash();
        levelDBService.put(key, block.toBytes());

        levelDBService.put(LATEST_BLOCK_KEY, block.getCurrentHash().getBytes());
        log.debug("Block saved: {}", block.getCurrentHash());
    }

    public Optional<Block> findByHash(String hash) {
        try {
            byte[] data = levelDBService.get(BLOCK_PREFIX + hash);
            if (data == null) return Optional.empty();
            return Optional.of(Block.fromBytes(data));
        } catch (Exception e) {
            log.error("Error loading block: {}", hash, e);
            return Optional.empty();
        }
    }

    public Optional<Block> findLatest() {
        try {
            byte[] hashBytes = levelDBService.get(LATEST_BLOCK_KEY);
            if (hashBytes == null) return Optional.empty();
            String hash = new String(hashBytes);
            return findByHash(hash);
        } catch (Exception e) {
            log.error("Error loading latest block", e);
            return Optional.empty();
        }
    }

    public List<Block> findAllByKeyIn(List<String> keys) {
        try {
            List<byte[]> data = levelDBService.findAllByKeysIn(keys);

            List<Block> blocks = new ArrayList<>(data.size());
            for (byte[] bytes : data) {
                if (bytes != null) {
                    Block block = Block.fromBytes(bytes);
                    blocks.add(block);
                }
            }

            log.debug("Converted {} byte arrays to blocks", blocks.size());
            return blocks;

        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to deserialize blocks", e);
            return Collections.emptyList();
        }
    }


}
