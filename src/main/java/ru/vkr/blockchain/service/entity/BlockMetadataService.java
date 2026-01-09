package ru.vkr.blockchain.service.entity;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.entity.BlockMetadata;
import ru.vkr.blockchain.repository.entity.BlockMetadataRepository;

import java.util.List;

/**
 * <br>
 * <strong>
 * Author: Дмитрий Николаенков (laplas7)
 * Creation date: 09.01.2026 23:54
 * </strong>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BlockMetadataService {
    private final BlockMetadataRepository blockMetadataRepository;

    public List<BlockMetadata> findBlockByHeightIn(int startHeight, int endHeight) {
        return blockMetadataRepository.findAllByHeightBetween(startHeight, endHeight);
    }
}
