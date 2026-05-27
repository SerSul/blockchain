package ru.vkr.blockchain.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.dto.CreateAccountPayload;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
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

    @RequireRole(AccountRole.VALIDATOR)
    public void createAccount(CreateTransactionRequest createTransactionRequest) {
        CreateAccountPayload payload = parsePayload(createTransactionRequest.getPayload(), CreateAccountPayload.class);
        var newUserPublicKeyBase64 = payload.getPublicKey();
        var newUserAddress = cryptoService.generateAddress(newUserPublicKeyBase64);

        Object lock = accountLocks.computeIfAbsent(newUserAddress, k -> new Object());

        synchronized (lock) {
            try {
                if (!cryptoService.checkSignatureValid(
                        createTransactionRequest.getPayload(),
                        createTransactionRequest.getSignature(),
                        createTransactionRequest.getCreatorPublicKey())) {
                    throw new SecurityException("Invalid signature for account creation");
                }

                if (accountRepository.findByAddress(newUserAddress).isPresent()) {
                    throw new IllegalArgumentException(
                            "Account with this public key already exists: " + newUserAddress);
                }

                boolean hasPendingCreation = pendingTransactionRepository.findAll().stream()
                        .anyMatch(tx -> tx.getTransactionType() == TransactionType.CREATE_ACCOUNT
                                && addressFromCreateAccountPayload(tx.getPayload()).equals(newUserAddress));

                if (hasPendingCreation) {
                    throw new IllegalArgumentException(
                            "Account creation already pending for address: " + newUserAddress);
                }
                Transaction transaction = transactionService.createTransaction(createTransactionRequest);
                pendingTransactionRepository.save(transaction);

            } finally {
                accountLocks.remove(newUserAddress, lock);
            }
        }
    }

    private String addressFromCreateAccountPayload(String payloadJson) {
        return cryptoService.generateAddress(parsePayload(payloadJson, CreateAccountPayload.class).getPublicKey());
    }

    @RequireRole(AccountRole.VALIDATOR)
    public void updateAccountRoles(CreateTransactionRequest request) {
        UpdateAccountRolesPayload payload = parsePayload(request.getPayload(), UpdateAccountRolesPayload.class);
        String targetAddress = payload.getTargetAddress();

        if (accountRepository.isBootstrapValidator(targetAddress)) {
            throw new IllegalArgumentException("Cannot change roles of bootstrap validator: " + targetAddress);
        }

        Object lock = accountLocks.computeIfAbsent(targetAddress, k -> new Object());
        synchronized (lock) {
            try {
                if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
                    throw new SecurityException("Invalid signature for transaction");
                }

                var target = accountRepository.findByAddress(targetAddress)
                        .orElseThrow(() -> new UserNotFoundException(targetAddress));

                if (!target.isActive()) {
                    throw new IllegalArgumentException("Cannot update roles for inactive account: " + targetAddress);
                }

                if (payload.getRoles().isEmpty()) {
                    throw new IllegalArgumentException("Roles list cannot be empty");
                }

                boolean hadValidator = target.getAccountRoles().contains(AccountRole.VALIDATOR);
                boolean willHaveValidator = payload.getRoles().contains(AccountRole.VALIDATOR);
                if (hadValidator && !willHaveValidator
                        && accountRepository.getValidators().contains(targetAddress)
                        && accountRepository.getValidators().size() <= 1) {
                    throw new IllegalArgumentException("Cannot remove the last validator from the network");
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

    @RequireRole(AccountRole.VALIDATOR)
    public void deactivateAccount(CreateTransactionRequest request) {
        DeactivateAccountPayload payload = parsePayload(request.getPayload(), DeactivateAccountPayload.class);
        String targetAddress = payload.getTargetAddress();
        String creatorAddress = cryptoService.generateAddress(request.getCreatorPublicKey());

        if (targetAddress.equals(creatorAddress)) {
            throw new IllegalArgumentException("Cannot deactivate your own account");
        }

        if (accountRepository.isBootstrapValidator(targetAddress)) {
            throw new IllegalArgumentException("Cannot deactivate bootstrap validator: " + targetAddress);
        }

        Object lock = accountLocks.computeIfAbsent(targetAddress, k -> new Object());
        synchronized (lock) {
            try {
                if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
                    throw new SecurityException("Invalid signature for transaction");
                }

                var target = accountRepository.findByAddress(targetAddress)
                        .orElseThrow(() -> new UserNotFoundException(targetAddress));

                if (!target.isActive()) {
                    throw new IllegalArgumentException("Account is already inactive: " + targetAddress);
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
