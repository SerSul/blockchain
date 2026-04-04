package ru.vkr.blockchain.repository.entity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.vkr.blockchain.domain.entity.TransactionMetadata;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;

import java.util.List;

public interface TransactionMetadataRepository extends JpaRepository<TransactionMetadata, String>, JpaSpecificationExecutor<TransactionMetadata> {

    List<TransactionMetadata> findByBlockHash(String blockHash);

    Page<TransactionMetadata> findByBlockHash(String blockHash, Pageable pageable);

    Page<TransactionMetadata> findBySenderId(String senderId, Pageable pageable);

    Page<TransactionMetadata> findByStatus(TransactionStatus status, Pageable pageable);

    Page<TransactionMetadata> findByTransactionType(TransactionType transactionType, Pageable pageable);
}
