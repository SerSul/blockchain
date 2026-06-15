package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.entity.TransactionMetadata;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.mapper.TransactionMapper;
import ru.vkr.blockchain.repository.entity.TransactionMetadataRepository;
import ru.vkr.blockchain.service.domain.BlockService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FileVersionService {

    private final TransactionMetadataRepository transactionMetadataRepository;
    private final BlockService blockService;
    private final TransactionMapper transactionMapper;

    /**
     * Цепочка версий из подтверждённых STORE_FILE: поле previous_transaction_id в payload транзакции.
     */
    public List<TransactionDto> getVersionChain(String fileHash) {
        List<TransactionMetadata> forHash = transactionMetadataRepository
                .findByFileHashAndTransactionTypeOrderByTimestampAsc(fileHash, TransactionType.STORE_FILE);
        if (forHash.isEmpty()) {
            return List.of();
        }
        TransactionMetadata latest = forHash.get(forHash.size() - 1);
        List<TransactionMetadata> chain = new ArrayList<>();
        TransactionMetadata current = latest;
        while (current != null) {
            chain.add(0, current);
            String parentId = current.getPreviousTransactionId();
            current = (parentId == null || parentId.isBlank())
                    ? null
                    : transactionMetadataRepository.findById(parentId).orElse(null);
        }
        return chain.stream().map(this::toEnrichedDto).toList();
    }

    public Optional<TransactionMetadata> findLatestStoreFile(String fileHash) {
        return transactionMetadataRepository
                .findByFileHashAndTransactionTypeOrderByTimestampAsc(fileHash, TransactionType.STORE_FILE)
                .stream()
                .max(Comparator.comparing(TransactionMetadata::getTimestamp));
    }

    private TransactionDto toEnrichedDto(TransactionMetadata metadata) {
        TransactionDto dto = transactionMapper.toDto(metadata);
        dto.setFileHash(metadata.getFileHash());
        dto.setPreviousTransactionId(metadata.getPreviousTransactionId());
        if (metadata.getBlockHash() != null) {
            blockService.findByHash(metadata.getBlockHash())
                    .map(Block::getTransactions)
                    .flatMap(txs -> txs.stream().filter(tx -> tx.getId().equals(metadata.getId())).findFirst())
                    .map(Transaction::getPayload)
                    .ifPresent(dto::setPayload);
        }
        return dto;
    }
}
