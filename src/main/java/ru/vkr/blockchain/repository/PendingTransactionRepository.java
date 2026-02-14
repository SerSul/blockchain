package ru.vkr.blockchain.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.service.LevelDBService;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class PendingTransactionRepository {

    private final LevelDBService levelDBService;

    private static final String PENDING_TX_PREFIX = "pending_tx:";

    public static String key(String transactionId) {
        return PENDING_TX_PREFIX + transactionId;
    }

    public void save(Transaction transaction) {
        try {
            String k = key(transaction.getId());
            levelDBService.put(k, transaction.toBytes());
            log.debug("Saved pending transaction: {}", transaction.getId());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save pending transaction", e);
        }
    }

    public void delete(String transactionId) {
        levelDBService.delete(key(transactionId));
        log.debug("Deleted pending transaction: {}", transactionId);
    }

    public void deleteAll(Collection<String> transactionIds) {
        Map<String, byte[]> ops = transactionIds.stream()
                .collect(java.util.stream.Collectors.toMap(PendingTransactionRepository::key, k -> (byte[]) null));
        levelDBService.batchWrite(ops);
        log.debug("Deleted {} pending transactions", transactionIds.size());
    }

    public Collection<Transaction> findAll() {
        Map<String, byte[]> entries = levelDBService.scanPrefix(PENDING_TX_PREFIX);
        return entries.values().stream()
                .map(bytes -> {
                    try {
                        return Transaction.fromBytes(bytes);
                    } catch (IOException | ClassNotFoundException e) {
                        log.error("Failed to deserialize pending transaction", e);
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    public boolean hasPendingForTarget(String targetAddress, TransactionType type) {
        return findAll().stream()
                .anyMatch(tx -> tx.getTransactionType() == type && tx.getPayload().contains(targetAddress));
    }

    public Collection<Transaction> findByIds(Collection<String> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return List.of();
        }
        return findAll().stream()
                .filter(tx -> transactionIds.contains(tx.getId()))
                .toList();
    }
}
