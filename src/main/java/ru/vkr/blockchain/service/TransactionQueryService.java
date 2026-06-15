package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.entity.TransactionMetadata;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.PageResponse;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.mapper.TransactionMapper;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.repository.entity.TransactionMetadataRepository;
import ru.vkr.blockchain.service.domain.BlockService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionMetadataRepository transactionMetadataRepository;
    private final PendingTransactionRepository pendingTransactionRepository;
    private final BlockService blockService;
    private final TransactionMapper transactionMapper;

    /**
     * Список транзакций с фильтрами и пагинацией.
     * status=PENDING — из пула ожидающих (пагинация в памяти), иначе из metadata (подтверждённые).
     */
    public PageResponse<TransactionDto> getTransactions(TransactionStatus status, String blockHash, String senderId,
                                                        TransactionType transactionType, int page, int size, String sortBy, String sortDir) {
        if (status == TransactionStatus.PENDING) {
            return getPendingTransactionsPage(page, size);
        }
        return getConfirmedTransactions(status, blockHash, senderId, transactionType, page, size, sortBy, sortDir);
    }

    /**
     * Pending-транзакции из LevelDB с пагинацией (новые сверху).
     */
    public PageResponse<TransactionDto> getPendingTransactionsPage(int page, int size) {
        List<Transaction> all = new ArrayList<>(pendingTransactionRepository.findAll());
        all.sort(Comparator.comparing(Transaction::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        int total = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 1;
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<TransactionDto> content = all.subList(from, to).stream()
                .map(transactionMapper::toDto)
                .toList();
        return new PageResponse<>(content, total, totalPages, page, size);
    }

    private PageResponse<TransactionDto> getConfirmedTransactions(TransactionStatus status, String blockHash,
                                                                   String senderId, TransactionType transactionType,
                                                                   int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir != null && sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy != null ? sortBy : "timestamp").ascending()
                : Sort.by(sortBy != null ? sortBy : "timestamp").descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        boolean hasFilter = status != null || (blockHash != null && !blockHash.isBlank())
                || (senderId != null && !senderId.isBlank()) || transactionType != null;

        Page<TransactionMetadata> result;
        if (!hasFilter) {
            result = transactionMetadataRepository.findAll(pageable);
        } else {
            Specification<TransactionMetadata> spec = (root, query, cb) -> {
                var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                if (status != null) {
                    predicates.add(cb.equal(root.get("status"), status));
                }
                if (blockHash != null && !blockHash.isBlank()) {
                    predicates.add(cb.equal(root.get("blockHash"), blockHash));
                }
                if (senderId != null && !senderId.isBlank()) {
                    predicates.add(cb.equal(root.get("senderId"), senderId));
                }
                if (transactionType != null) {
                    predicates.add(cb.equal(root.get("transactionType"), transactionType));
                }
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
            result = transactionMetadataRepository.findAll(spec, pageable);
        }
        List<TransactionDto> content = result.getContent().stream()
                .map(transactionMapper::toDto)
                .toList();
        return new PageResponse<>(
                content,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    /**
     * Одна транзакция по ID. Ищет в подтверждённых (metadata + блок) и в pending.
     */
    public Optional<TransactionDto> getTransactionById(String id) {
        Optional<TransactionMetadata> meta = transactionMetadataRepository.findById(id);
        if (meta.isPresent()) {
            TransactionDto dto = transactionMapper.toDto(meta.get());
            dto.setFileHash(meta.get().getFileHash());
            dto.setPreviousTransactionId(meta.get().getPreviousTransactionId());
            blockService.findByHash(meta.get().getBlockHash())
                    .map(Block::getTransactions)
                    .stream().flatMap(List::stream)
                    .filter(tx -> tx.getId().equals(id))
                    .findFirst()
                    .ifPresent(tx -> dto.setPayload(tx.getPayload()));
            return Optional.of(dto);
        }
        return pendingTransactionRepository.findAll().stream()
                .filter(tx -> tx.getId().equals(id))
                .findFirst()
                .map(transactionMapper::toDto);
    }
}
