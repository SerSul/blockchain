package ru.vkr.blockchain.service;


import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import ru.vkr.blockchain.domain.entity.BlockMetadata;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.service.domain.BlockService;
import ru.vkr.blockchain.service.entity.BlockMetadataService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <br>
 * <strong>
 * Author: Дмитрий Николаенков (laplas7)
 * Creation date: 09.01.2026 23:19
 * </strong>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BlockChainService {

    private final BlockService blockService;
    private final BlockMetadataService blockMetadataService;

    @Getter
    @Setter
    private volatile Block latestBlock;

    @Getter
    private final int HOT_BLOCKS_SIZE = 100;

    @Getter
    private final LinkedHashMap<String, Block> hotBlocksCache = new LinkedHashMap<>(HOT_BLOCKS_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Block> eldest) {
            return size() > HOT_BLOCKS_SIZE;
        }
    };

    @Getter
    private final ConcurrentHashMap<Integer, String> heightToHashCache = new ConcurrentHashMap<>();

    @Getter
    private final ConcurrentHashMap<String, Transaction> pendingTransactions = new ConcurrentHashMap<>();

    /**
     * Method finds latest block in blockchain and if present set latest HOT_BLOCKS_SIZE in hashMap for cache
     * */
    @PostConstruct
    private void init() {
        blockService.findLatest()
                .ifPresentOrElse(
                        block -> {
                            setLatestBlock(block);
                            loadHotBlocks();
                            log.info("Latest block loaded: height={}, hash={}",
                                    block.getHeight(), block.getCurrentHash());
                        },
                        () -> log.warn("No blocks found. Genesis block required!")
                );
    }

    /**
     * Method load latest HOT_BLOCKS_SIZE in hashMap
     * */
    private void loadHotBlocks() {
        if (latestBlock == null) return;
        int startHeight = Math.max(0, latestBlock.getHeight() - HOT_BLOCKS_SIZE + 1);
        var latestBlocksMetadata = blockMetadataService.findBlockByHeightIn(startHeight, latestBlock.getHeight());
        if (CollectionUtils.isEmpty(latestBlocksMetadata)) return;
        var blocksKeys = latestBlocksMetadata.stream().map(BlockMetadata::getLeveldbKey).toList();
        blockService.findAllByKeyIn(blocksKeys)
                .forEach(block -> {
                    hotBlocksCache.put(block.getCurrentHash(), block);
                    heightToHashCache.put(block.getHeight(), block.getCurrentHash());
                });
    }


}
