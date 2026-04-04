package ru.vkr.blockchain.repository.entity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import ru.vkr.blockchain.domain.entity.BlockMetadata;

import java.util.List;

public interface BlockMetadataRepository extends JpaRepository<BlockMetadata, String>, JpaSpecificationExecutor<BlockMetadata> {

    @Query("SELECT b FROM BlockMetadata b WHERE b.height BETWEEN :startHeight AND :endHeight ORDER BY b.height ASC")
    List<BlockMetadata> findAllByHeightBetween(Integer startHeight, Integer endHeight);

    Page<BlockMetadata> findAllByOrderByHeightDesc(Pageable pageable);
}
