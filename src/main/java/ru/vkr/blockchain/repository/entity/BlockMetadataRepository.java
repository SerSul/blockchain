package ru.vkr.blockchain.repository.entity;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.vkr.blockchain.domain.entity.BlockMetadata;

import java.util.List;

/**
 * <br>
 * <strong>
 * Author: Дмитрий Николаенков (laplas7)
 * Creation date: 09.01.2026 23:35
 * </strong>
 */
public interface BlockMetadataRepository extends JpaRepository<BlockMetadata, Long> {

    @Query("SELECT b FROM BlockMetadata b WHERE b.height BETWEEN :startHeight AND :endHeight ORDER BY b.height ASC")
    List<BlockMetadata> findAllByHeightBetween(Integer startHeight, Integer endHeight);
}
