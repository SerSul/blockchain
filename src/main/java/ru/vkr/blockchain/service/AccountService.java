package ru.vkr.blockchain.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.DeactivateAccountPayload;
import ru.vkr.blockchain.dto.UpdateAccountRolesPayload;
import ru.vkr.blockchain.exception.user.UserNotFoundException;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.service.domain.TransactionService;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final PendingTransactionRepository pendingTransactionRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    @Getter
    private final ConcurrentHashMap<String, Object> accountLocks = new ConcurrentHashMap<>();


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

                boolean hasPendingCreation = pendingTransactionRepository.findAll().stream()
                        .anyMatch(tx -> tx.getTransactionType() == TransactionType.CREATE_ACCOUNT
                                && cryptoService.generateAddress(tx.getPayload()).equals(newUserAddress));

                if (hasPendingCreation) {
                    throw new IllegalArgumentException(
                            "Account creation already pending for address: " + newUserAddress);
                }
                var transaction = transactionService.createTransaction(createTransactionRequest);
                pendingTransactionRepository.save(transaction);

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

                if (pendingTransactionRepository.hasPendingForTarget(targetAddress, TransactionType.UPDATE_ACCOUNT_ROLES)) {
                    throw new IllegalArgumentException("Update roles already pending for address: " + targetAddress);
                }

                Transaction transaction = transactionService.createTransaction(request);
                pendingTransactionRepository.save(transaction);
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

                if (pendingTransactionRepository.hasPendingForTarget(targetAddress, TransactionType.DEACTIVATE_ACCOUNT)) {
                    throw new IllegalArgumentException("Deactivation already pending for address: " + targetAddress);
                }

                Transaction transaction = transactionService.createTransaction(request);
                pendingTransactionRepository.save(transaction);
            } finally {
                accountLocks.remove(targetAddress, lock);
            }
        }
    }

    private <T> T parsePayload(String payloadJson, Class<T> clazz) {
        try {
            return objectMapper.readValue(payloadJson, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload format: " + e.getMessage());
        }
    }
}
