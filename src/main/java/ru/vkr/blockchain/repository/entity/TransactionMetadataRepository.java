package ru.vkr.blockchain.repository.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.blockchain.domain.entity.TransactionMetadata;

import java.util.List;

public interface TransactionMetadataRepository extends JpaRepository<TransactionMetadata, String> {

    List<TransactionMetadata> findByBlockHash(String blockHash);

    List<TransactionMetadata> findBySenderId(String senderId);
}
