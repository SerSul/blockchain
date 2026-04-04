package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.dto.CreateBlockRequest;
import ru.vkr.blockchain.dto.PrepareBlockResponse;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.BlockStatus;
import ru.vkr.blockchain.domain.entity.AuditLog;
import ru.vkr.blockchain.domain.entity.BlockMetadata;
import ru.vkr.blockchain.domain.entity.TransactionMetadata;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.repository.entity.AuditLogRepository;
import ru.vkr.blockchain.repository.entity.BlockMetadataRepository;
import ru.vkr.blockchain.repository.entity.TransactionMetadataRepository;
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

    private static final String GENESIS_PREVIOUS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String ENTITY_BLOCK = "BLOCK";

    private final BlockService blockService;
    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final PendingTransactionRepository pendingTransactionRepository;
    private final TransactionApplierService transactionApplierService;
    private final AuditLogRepository auditLogRepository;
    private final BlockMetadataRepository blockMetadataRepository;
    private final TransactionMetadataRepository transactionMetadataRepository;

    /**
     * Подготовка блока: возвращает хеш для подписи и данные (timestamp, transaction_ids)
     * для последующего вызова createBlock. Валидатор подписывает hash_to_sign и отправляет
     * createBlock с тем же timestamp и transaction_ids.
     */
    public PrepareBlockResponse prepareBlock(List<String> transactionIds) {
        String validatorAddress = getNextValidatorAddress()
                .orElseThrow(() -> new IllegalStateException("No validators in the network"));
        List<Transaction> transactions = resolveTransactions(transactionIds);
        LocalDateTime timestamp = LocalDateTime.now();
        Block block = buildBlock(validatorAddress, timestamp, transactions);
        String hashToSign = block.calculateHash();
        List<String> idsInOrder = transactions.stream().map(Transaction::getId).toList();
        return new PrepareBlockResponse(hashToSign, timestamp, block.getHeight(), idsInOrder);
    }

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
        blockMetadataRepository.save(toBlockMetadata(block));
        applyTransactions(transactions);
        for (Transaction tx : transactions) {
            transactionMetadataRepository.save(toTransactionMetadata(tx, block.getCurrentHash()));
        }
        pendingTransactionRepository.deleteAll(transactions.stream().map(Transaction::getId).toList());

        auditLogRepository.save(new AuditLog(ENTITY_BLOCK, block.getCurrentHash(), "CREATE_BLOCK", validatorAddress,
                "height=" + block.getHeight() + ", txCount=" + transactions.size()));
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

    /**
     * Создаёт детерминированный genesis-блок (height=0).
     * Если передан непустой список транзакций (bootstrap), они включаются в блок и применяются.
     */
    public void createGenesisBlock(String validatorAddress, List<Transaction> transactions) {
        if (blockService.findLatest().isPresent()) {
            throw new IllegalStateException("Genesis block already exists");
        }
        List<Transaction> txList = transactions != null ? transactions : List.of();
        String merkleRoot = cryptoService.calculateMerkleRoot(
                txList.stream().map(Transaction::calculateHash).toList());
        Block block = createBlockStructure(0, GENESIS_PREVIOUS_HASH, validatorAddress, merkleRoot, txList);
        block.setCurrentHash(block.calculateHash());
        block.setValidatorSignature("");
        block.setStatus(BlockStatus.CONFIRMED);
        block.setTimestamp(block.getTimestamp() != null ? block.getTimestamp() : java.time.LocalDateTime.now());
        blockService.save(block);
        blockMetadataRepository.save(toBlockMetadata(block));
        if (!txList.isEmpty()) {
            applyTransactions(txList);
            for (Transaction tx : txList) {
                transactionMetadataRepository.save(toTransactionMetadata(tx, block.getCurrentHash()));
            }
        }
        auditLogRepository.save(new AuditLog(ENTITY_BLOCK, block.getCurrentHash(), "CREATE_GENESIS", validatorAddress,
                "height=0, txCount=" + txList.size()));
        log.info("Genesis block created: hash={}, txCount={}", block.getCurrentHash(), txList.size());
    }

    private BlockMetadata toBlockMetadata(Block block) {
        BlockMetadata m = new BlockMetadata();
        m.setHash(block.getCurrentHash());
        m.setPreviousHash(block.getPreviousHash());
        m.setHeight(block.getHeight());
        m.setMerkleRoot(block.getMerkleRoot());
        m.setValidatorAddress(block.getValidatorAddress());
        m.setValidatorSignature(block.getValidatorSignature() != null ? block.getValidatorSignature() : "");
        m.setTransactionCount(block.getTransactions() != null ? block.getTransactions().size() : 0);
        m.setStatus(block.getStatus());
        m.setTimestamp(block.getTimestamp());
        m.setLeveldbKey("block:" + block.getCurrentHash());
        return m;
    }

    private TransactionMetadata toTransactionMetadata(Transaction tx, String blockHash) {
        TransactionMetadata m = new TransactionMetadata();
        m.setId(tx.getId());
        m.setSenderId(tx.getSenderId());
        m.setTransactionType(tx.getTransactionType());
        m.setStatus(tx.getStatus());
        m.setBlockHash(blockHash);
        m.setPayloadHash(tx.getPayloadHash());
        m.setContentType(tx.getContentType());
        m.setContentSize(tx.getPayload() != null ? (long) tx.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length : 0L);
        m.setTimestamp(tx.getTimestamp());
        m.setLeveldbKey("tx:" + tx.getId());
        return m;
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
