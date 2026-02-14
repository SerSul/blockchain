package ru.vkr.blockchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.DeactivateAccountPayload;
import ru.vkr.blockchain.dto.UpdateAccountRolesPayload;
import ru.vkr.blockchain.exception.user.UserNotFoundException;
import ru.vkr.blockchain.repository.AccountRepository;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Применяет транзакции к состоянию при создании блока.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionApplierService {

    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    /**
     * Применяет транзакцию к состоянию блокчейна.
     */
    public void apply(Transaction transaction) {
        if (!transaction.isValid()) {
            throw new IllegalArgumentException("Invalid transaction: " + transaction.getId());
        }

        switch (transaction.getTransactionType()) {
            case CREATE_ACCOUNT -> applyCreateAccount(transaction);
            case UPDATE_ACCOUNT_ROLES -> applyUpdateAccountRoles(transaction);
            case DEACTIVATE_ACCOUNT -> applyDeactivateAccount(transaction);
            case STORE_DATA -> applyStoreData(transaction);
        }

        transaction.setStatus(TransactionStatus.CONFIRMED);
        log.debug("Applied transaction {} type={}", transaction.getId(), transaction.getTransactionType());
    }

    private void applyCreateAccount(Transaction tx) {
        String publicKeyBase64 = tx.getPayload();
        String address = cryptoService.generateAddress(publicKeyBase64);

        if (accountRepository.findByAddress(address).isPresent()) {
            throw new IllegalStateException("Account already exists: " + address);
        }

        Account account = new Account(address, publicKeyBase64, tx.getSenderId());
        try {
            accountRepository.save(account);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save account", e);
        }
    }

    private void applyUpdateAccountRoles(Transaction tx) {
        UpdateAccountRolesPayload payload = parsePayload(tx.getPayload(), UpdateAccountRolesPayload.class);
        Account account = accountRepository.findByAddress(payload.getTargetAddress())
                .orElseThrow(() -> new UserNotFoundException(payload.getTargetAddress()));

        if (!account.isActive()) {
            throw new IllegalStateException("Cannot update roles for inactive account: " + payload.getTargetAddress());
        }
        if (account.getAccountRoles().contains(AccountRole.ADMIN)) {
            throw new IllegalStateException("Cannot modify admin account");
        }

        account.setAccountRoles(new ArrayList<>(payload.getRoles()));
        try {
            accountRepository.update(account);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update account", e);
        }
    }

    private void applyDeactivateAccount(Transaction tx) {
        DeactivateAccountPayload payload = parsePayload(tx.getPayload(), DeactivateAccountPayload.class);
        Account account = accountRepository.findByAddress(payload.getTargetAddress())
                .orElseThrow(() -> new UserNotFoundException(payload.getTargetAddress()));

        if (!account.isActive()) {
            throw new IllegalStateException("Account already inactive: " + payload.getTargetAddress());
        }
        if (account.getAccountRoles().contains(AccountRole.ADMIN)) {
            throw new IllegalStateException("Cannot deactivate admin account");
        }

        account.setActive(false);
        try {
            accountRepository.update(account);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deactivate account", e);
        }
    }

    private void applyStoreData(Transaction tx) {
        // STORE_DATA — данные уже в payload транзакции, блок сохраняет их.
        // Дополнительное состояние не меняется.
    }

    private <T> T parsePayload(String payloadJson, Class<T> clazz) {
        try {
            return objectMapper.readValue(payloadJson, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload format: " + e.getMessage());
        }
    }
}
