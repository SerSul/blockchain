package ru.vkr.blockchain.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.service.LevelDBService;

import java.io.IOException;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class TransactionRepository {

    private final LevelDBService levelDBService;
    private static final String TX_PREFIX = "tx:";

    public void save(Transaction transaction) throws IOException {
        String key = TX_PREFIX + transaction.getId();
        levelDBService.put(key, transaction.toBytes());
        log.debug("Transaction saved: {}", transaction.getId());
    }

    public Optional<Transaction> findById(String id) {
        try {
            byte[] data = levelDBService.get(TX_PREFIX + id);
            if (data == null) return Optional.empty();
            return Optional.of(Transaction.fromBytes(data));
        } catch (Exception e) {
            log.error("Error loading transaction: {}", id, e);
            return Optional.empty();
        }
    }
}
