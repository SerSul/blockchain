package ru.vkr.blockchain.service;


import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.api.CreateTransactionRequest;
import ru.vkr.blockchain.domain.entity.BlockMetadata;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.service.domain.BlockService;
import ru.vkr.blockchain.service.domain.TransactionService;
import ru.vkr.blockchain.service.entity.BlockMetadataService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <br>
 * <strong>
 * Author: Дмитрий Николаенков (laplas7)
 * Creation date: 09.01.2026 23:19
 * </strong>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BlockChainService {

    private final BlockService blockService;
    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;

    @Getter
    @Setter
    private volatile Block latestBlock;

    @Getter
    private final int HOT_BLOCKS_SIZE = 100; // todo вынести в переменные окружения

    @Getter
    private final Map<String, Transaction> pendingTransactions =
            Collections.synchronizedMap(new LinkedHashMap<>());

    @Getter
    private final ConcurrentHashMap<String, Object> accountLocks = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        blockService.findLatest()
                .ifPresentOrElse(
                        block -> {
                            setLatestBlock(block);
                            log.info("Latest block loaded: height={}, hash={}",
                                    block.getHeight(), block.getCurrentHash());
                        },
                        () -> log.warn("No blocks found. Genesis block required!")
                );
    }

    @RequireRole({AccountRole.ADMIN, AccountRole.VALIDATOR})
    public void createAccount(CreateTransactionRequest createTransactionRequest) {

        var newUserPublicKeyBase64 = createTransactionRequest.getPayload();
        var newUserAddress = cryptoService.generateAddress(newUserPublicKeyBase64);

        Object lock = accountLocks.computeIfAbsent(newUserAddress, k -> new Object());

        synchronized (lock) {
            try {
                boolean isSignatureValid = cryptoService.checkSignatureValid(
                        createTransactionRequest.getPayload(),
                        createTransactionRequest.getSignature(),
                        createTransactionRequest.getCreatorPublicKey()
                );

                if (!isSignatureValid) {
                    throw new SecurityException("Invalid signature for account creation");
                }

                if (accountRepository.findByAddress(newUserAddress).isPresent()) {
                    throw new IllegalArgumentException(
                            "Account with this public key already exists: " + newUserAddress);
                }

                boolean hasPendingCreation = getPendingTransactions().values().stream()
                        .anyMatch(tx -> tx.getTransactionType() == TransactionType.CREATE_ACCOUNT);

                if (hasPendingCreation) {
                    throw new IllegalArgumentException(
                            "Account creation already pending for address: " + newUserAddress);
                }
                var transaction = transactionService.createTransaction(createTransactionRequest);
                getPendingTransactions().put(transaction.getId(), transaction);

            } finally {
                accountLocks.remove(newUserAddress, lock);
            } // todo создавать auditLog
        }
    }

}
