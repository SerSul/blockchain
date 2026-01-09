package ru.vkr.blockchain.service.domain;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.repository.BlockRepository;

import java.util.List;
import java.util.Optional;

/**
 * <br>
 * <strong>
 * Author: Дмитрий Николаенков (laplas7)
 * Creation date: 10.01.2026 00:48
 * </strong>
 */
@Service
@RequiredArgsConstructor
public class BlockService {
    private final BlockRepository blockRepository;

    public Optional<Block> findByHash(String hash) {
        return blockRepository.findByHash(hash);
    }

    public Optional<Block> findLatest() {
        return blockRepository.findLatest();
    }

    public List<Block> findAllByKeyIn(List<String> keys) {
        return blockRepository.findAllByKeyIn(keys);
    }

    public void save(Block block) {
        try {
            blockRepository.save(block);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
