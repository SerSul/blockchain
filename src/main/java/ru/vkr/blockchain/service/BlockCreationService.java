package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.dto.CreateBlockRequest;
import ru.vkr.blockchain.dto.PrepareBlockResponse;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.BlockStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
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
    private static final LocalDateTime GENESIS_TIMESTAMP = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String ENTITY_BLOCK = "BLOCK";
    private static final String SYSTEM_ACTOR = "system";

    private final BlockService blockService;
    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final LevelDBService levelDBService;
    private final PendingTransactionRepository pendingTransactionRepository;
    private final TransactionApplierService transactionApplierService;
    private final AuditLogRepository auditLogRepository;
    private final BlockMetadataRepository blockMetadataRepository;
    private final TransactionMetadataRepository transactionMetadataRepository;
    private final StoreDataPayloadValidator storeDataPayloadValidator;

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
        createGenesisBlock(validatorAddress, transactions, GENESIS_TIMESTAMP);
    }

    public void createGenesisBlock(String validatorAddress, List<Transaction> transactions, LocalDateTime timestamp) {
        if (blockService.findLatest().isPresent()) {
            throw new IllegalStateException("Genesis block already exists");
        }
        List<Transaction> txList = transactions != null ? transactions : List.of();
        String merkleRoot = cryptoService.calculateMerkleRoot(
                txList.stream().map(Transaction::calculateHash).toList());
        Block block = createBlockStructure(0, GENESIS_PREVIOUS_HASH, validatorAddress, merkleRoot, txList);
        block.setTimestamp(timestamp != null ? timestamp : GENESIS_TIMESTAMP);
        block.setCurrentHash(block.calculateHash());
        block.setValidatorSignature("");
        block.setStatus(BlockStatus.CONFIRMED);
        persistCanonicalBlock(block, true, "CREATE_GENESIS");
        log.info("Genesis block created: hash={}, txCount={}", block.getCurrentHash(), txList.size());
    }

    public ImportResult importExternalBlock(Block block, String sourcePeer) {
        if (block == null || block.getCurrentHash() == null || block.getCurrentHash().isBlank()) {
            log.error("Rejected external block as INVALID: empty block/hash, source={}", sourcePeer);
            auditImportEvent(block, "IMPORT_INVALID", sourcePeer, "empty block/hash");
            return ImportResult.INVALID;
        }
        if (!block.validate()) {
            log.error("Rejected external block as INVALID: validation failed, hash={}, height={}, source={}",
                    block.getCurrentHash(), block.getHeight(), sourcePeer);
            auditImportEvent(block, "IMPORT_INVALID", sourcePeer, "block.validate() failed");
            return ImportResult.INVALID;
        }
        if (blockService.findByHash(block.getCurrentHash()).isPresent()) {
            log.info("Ignored external block as ALREADY_EXISTS: hash={}, height={}, source={}",
                    block.getCurrentHash(), block.getHeight(), sourcePeer);
            auditImportEvent(block, "IMPORT_ALREADY_EXISTS", sourcePeer, "block already present");
            return ImportResult.ALREADY_EXISTS;
        }
        Optional<Block> latestOpt = blockService.findLatest();
        if (latestOpt.isEmpty()) {
            if (block.getHeight() != 0 || !GENESIS_PREVIOUS_HASH.equals(block.getPreviousHash())) {
                storeForkCandidate(block, "non-genesis on empty local chain");
                auditImportEvent(block, "IMPORT_FORK_CANDIDATE", sourcePeer, "non-genesis on empty local chain");
                return ImportResult.FORK_CANDIDATE;
            }
            persistCanonicalBlock(block, true, "SYNC_IMPORT");
            auditImportEvent(block, "IMPORT_ACCEPTED", sourcePeer, "imported as genesis on empty chain");
            return ImportResult.IMPORTED;
        }

        Block latest = latestOpt.get();
        boolean extendsTip = latest.getCurrentHash().equals(block.getPreviousHash())
                && block.getHeight() == latest.getHeight() + 1;
        if (!extendsTip) {
            storeForkCandidate(block, "previousHash mismatch, source=" + sourcePeer);
            auditImportEvent(block, "IMPORT_FORK_CANDIDATE", sourcePeer, "previousHash mismatch");
            return ImportResult.FORK_CANDIDATE;
        }

        persistCanonicalBlock(block, true, "SYNC_IMPORT");
        auditImportEvent(block, "IMPORT_ACCEPTED", sourcePeer, "extends tip and imported");
        return ImportResult.IMPORTED;
    }

    private void persistCanonicalBlock(Block block, boolean applyTx, String auditAction) {
        blockService.save(block);
        blockMetadataRepository.save(toBlockMetadata(block));
        if (applyTx && block.getTransactions() != null && !block.getTransactions().isEmpty()) {
            applyTransactions(block.getTransactions());
            for (Transaction tx : block.getTransactions()) {
                transactionMetadataRepository.save(toTransactionMetadata(tx, block.getCurrentHash()));
            }
            pendingTransactionRepository.deleteAll(block.getTransactions().stream().map(Transaction::getId).toList());
        }
        auditLogRepository.save(new AuditLog(
                ENTITY_BLOCK,
                block.getCurrentHash(),
                auditAction,
                block.getValidatorAddress(),
                "height=" + block.getHeight() + ", txCount=" + (block.getTransactions() != null ? block.getTransactions().size() : 0)));
    }

    private void storeForkCandidate(Block block, String reason) {
        try {
            String key = "fork_candidate:%08d:%s".formatted(block.getHeight(), block.getCurrentHash());
            levelDBService.put(key, block.toBytes());
            log.warn("Stored fork candidate block: h={}, hash={}, reason={}", block.getHeight(), block.getCurrentHash(), reason);
        } catch (Exception e) {
            log.error("Failed to store fork candidate {}", block.getCurrentHash(), e);
        }
    }

    private void auditImportEvent(Block block, String action, String sourcePeer, String details) {
        String blockHash = block != null && block.getCurrentHash() != null ? block.getCurrentHash() : "unknown";
        String actor = block != null && block.getValidatorAddress() != null ? block.getValidatorAddress() : SYSTEM_ACTOR;
        String info = "source=" + sourcePeer + ", " + details;
        auditLogRepository.save(new AuditLog(ENTITY_BLOCK, blockHash, action, actor, info));
    }

    public enum ImportResult {
        IMPORTED,
        ALREADY_EXISTS,
        FORK_CANDIDATE,
        INVALID
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
        if (tx.getTransactionType() == TransactionType.STORE_FILE && tx.getPayload() != null) {
            var filePayload = storeDataPayloadValidator.parse(tx.getPayload());
            m.setFileHash(filePayload.getFileHash());
            m.setPreviousTransactionId(filePayload.getPreviousTransactionId());
        }
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
