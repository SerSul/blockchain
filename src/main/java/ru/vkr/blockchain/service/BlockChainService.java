package ru.vkr.blockchain.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.api.CreateTransactionRequest;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.DeactivateAccountPayload;
import ru.vkr.blockchain.dto.UpdateAccountRolesPayload;
import ru.vkr.blockchain.exception.user.UserNotFoundException;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.service.domain.BlockService;
import ru.vkr.blockchain.service.domain.TransactionService;

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
    private final ObjectMapper objectMapper;

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
                        () -> log.warn("No blocks found. Genesis block required!") // todo change to Error
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
                        .anyMatch(tx -> tx.getTransactionType() == TransactionType.CREATE_ACCOUNT
                                && cryptoService.generateAddress(tx.getPayload()).equals(newUserAddress));

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

    @RequireRole(AccountRole.ADMIN)
    public void updateAccountRoles(CreateTransactionRequest request) {
        UpdateAccountRolesPayload payload = parsePayload(request.getPayload(), UpdateAccountRolesPayload.class);
        String targetAddress = payload.getTargetAddress();

        Object lock = accountLocks.computeIfAbsent(targetAddress, k -> new Object());
        synchronized (lock) {
            try {
                if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
                    throw new SecurityException("Invalid signature for transaction");
                }

                Account target = accountRepository.findByAddress(targetAddress)
                        .orElseThrow(() -> new UserNotFoundException(targetAddress));

                if (!target.isActive()) {
                    throw new IllegalArgumentException("Cannot update roles for inactive account: " + targetAddress);
                }

                if (target.getAccountRoles().contains(AccountRole.ADMIN)) {
                    throw new IllegalArgumentException("Admin cannot modify another admin");
                }

                if (payload.getRoles().isEmpty()) {
                    throw new IllegalArgumentException("Roles list cannot be empty");
                }

                if (hasPendingTransactionForTarget(targetAddress, TransactionType.UPDATE_ACCOUNT_ROLES)) {
                    throw new IllegalArgumentException("Update roles already pending for address: " + targetAddress);
                }

                Transaction transaction = transactionService.createTransaction(request);
                getPendingTransactions().put(transaction.getId(), transaction);
            } finally {
                accountLocks.remove(targetAddress, lock);
            }
        }
    }

    @RequireRole(AccountRole.ADMIN)
    public void deactivateAccount(CreateTransactionRequest request) {
        DeactivateAccountPayload payload = parsePayload(request.getPayload(), DeactivateAccountPayload.class);
        String targetAddress = payload.getTargetAddress();
        String creatorAddress = cryptoService.generateAddress(request.getCreatorPublicKey());

        if (targetAddress.equals(creatorAddress)) {
            throw new IllegalArgumentException("Cannot deactivate your own account");
        }

        Object lock = accountLocks.computeIfAbsent(targetAddress, k -> new Object());
        synchronized (lock) {
            try {
                if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
                    throw new SecurityException("Invalid signature for transaction");
                }

                Account target = accountRepository.findByAddress(targetAddress)
                        .orElseThrow(() -> new UserNotFoundException(targetAddress));

                if (!target.isActive()) {
                    throw new IllegalArgumentException("Account is already inactive: " + targetAddress);
                }

                if (target.getAccountRoles().contains(AccountRole.ADMIN)) {
                    throw new IllegalArgumentException("Admin cannot modify another admin");
                }

                if (hasPendingTransactionForTarget(targetAddress, TransactionType.DEACTIVATE_ACCOUNT)) {
                    throw new IllegalArgumentException("Deactivation already pending for address: " + targetAddress);
                }

                Transaction transaction = transactionService.createTransaction(request);
                getPendingTransactions().put(transaction.getId(), transaction);
            } finally {
                accountLocks.remove(targetAddress, lock);
            }
        }
    }

    @RequireRole({AccountRole.USER, AccountRole.VALIDATOR, AccountRole.ADMIN})
    public void storeData(CreateTransactionRequest request) {
        if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
            throw new SecurityException("Invalid signature for transaction");
        }

        Transaction transaction = transactionService.createTransaction(request);
        getPendingTransactions().put(transaction.getId(), transaction);
    }

    private <T> T parsePayload(String payloadJson, Class<T> clazz) {
        try {
            return objectMapper.readValue(payloadJson, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload format: " + e.getMessage());
        }
    }

    private boolean hasPendingTransactionForTarget(String targetAddress, TransactionType type) {
        return getPendingTransactions().values().stream()
                .anyMatch(tx -> tx.getTransactionType() == type && tx.getPayload().contains(targetAddress));
    }

}
