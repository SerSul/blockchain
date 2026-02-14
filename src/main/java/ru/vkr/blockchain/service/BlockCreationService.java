package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.dto.CreateBlockRequest;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.BlockStatus;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.service.domain.BlockService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сервис создания блоков и применения транзакций.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockCreationService {

    private static final String GENESIS_PREVIOUS_HASH = "0";

    private final BlockService blockService;
    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final PendingTransactionRepository pendingTransactionRepository;
    private final TransactionApplierService transactionApplierService;

    /**
     * Создаёт блок после проверки подписи валидатора.
     */
    public void createBlock(CreateBlockRequest request) {
        String validatorAddress = cryptoService.generateAddress(request.getValidatorPublicKey());

        if (!isCurrentValidator(validatorAddress)) {
            throw new IllegalArgumentException("Address is not the current validator: " + validatorAddress);
        }

        List<Transaction> transactions = resolveTransactions(request.getTransactionIds());
        Block block = buildBlock(validatorAddress, request.getTimestamp(), transactions);

        String blockHash = block.calculateHash();
        if (!cryptoService.checkSignatureValid(blockHash, request.getValidatorSignature(), request.getValidatorPublicKey())) {
            throw new SecurityException("Invalid validator signature");
        }

        block.setCurrentHash(blockHash);
        block.setValidatorSignature(request.getValidatorSignature());
        block.setStatus(BlockStatus.CONFIRMED);

        blockService.save(block);
        applyTransactions(transactions);
        pendingTransactionRepository.deleteAll(transactions.stream().map(Transaction::getId).toList());

        log.info("Block created: height={}, hash={}, txCount={}", block.getHeight(), block.getCurrentHash(), transactions.size());
    }

    private void applyTransactions(List<Transaction> transactions) {
        for (Transaction tx : transactions) {
            transactionApplierService.apply(tx);
        }
    }

    private Block buildBlock(String validatorAddress, LocalDateTime timestamp, List<Transaction> transactions) {
        String previousHash = getPreviousHash();
        int height = getNextHeight();
        String merkleRoot = computeMerkleRoot(transactions);

        Block block = createBlockStructure(height, previousHash, validatorAddress, merkleRoot, transactions);
        block.setTimestamp(timestamp);
        return block;
    }

    private Block createBlockStructure(int height, String previousHash, String validatorAddress, String merkleRoot, List<Transaction> transactions) {
        Block block = new Block(height, previousHash);
        block.setValidatorAddress(validatorAddress);
        block.setMerkleRoot(merkleRoot);

        for (Transaction tx : transactions) {
            block.addTransaction(tx);
        }
        return block;
    }

    private String computeMerkleRoot(List<Transaction> transactions) {
        List<String> hashes = transactions.stream()
                .map(Transaction::calculateHash)
                .toList();
        return cryptoService.calculateMerkleRoot(hashes);
    }

    private String getPreviousHash() {
        return blockService.findLatest()
                .map(Block::getCurrentHash)
                .orElse(GENESIS_PREVIOUS_HASH);
    }

    private int getNextHeight() {
        return blockService.findLatest()
                .map(b -> b.getHeight() + 1)
                .orElse(0);
    }

    private Optional<String> getNextValidatorAddress() {
        List<String> validators = accountRepository.getValidators();
        if (validators.isEmpty()) {
            return Optional.empty();
        }
        int index = getNextHeight() % validators.size();
        return Optional.of(validators.get(index));
    }

    private boolean isCurrentValidator(String address) {
        return getNextValidatorAddress()
                .map(addr -> addr.equals(address))
                .orElse(false);
    }

    private List<Transaction> resolveTransactions(List<String> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return new ArrayList<>(pendingTransactionRepository.findAll());
        }
        List<Transaction> found = new ArrayList<>(pendingTransactionRepository.findByIds(transactionIds));
        if (found.size() != transactionIds.size()) {
            throw new IllegalArgumentException("Some transaction IDs were not found in pending pool");
        }
        return reorderByRequestedIds(found, transactionIds);
    }

    private List<Transaction> reorderByRequestedIds(List<Transaction> transactions, List<String> requestedIds) {
        return requestedIds.stream()
                .map(id -> transactions.stream().filter(tx -> tx.getId().equals(id)).findFirst().orElseThrow())
                .toList();
    }
}
