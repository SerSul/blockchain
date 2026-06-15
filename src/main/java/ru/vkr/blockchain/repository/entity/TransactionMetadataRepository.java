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

    List<TransactionMetadata> findByFileHashAndTransactionTypeOrderByTimestampAsc(
            String fileHash, TransactionType transactionType);
}
