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
import ru.vkr.blockchain.dto.CreateAccountPayload;
import ru.vkr.blockchain.dto.DeactivateAccountPayload;
import ru.vkr.blockchain.dto.AddPeerPayload;
import ru.vkr.blockchain.dto.RemovePeerPayload;
import ru.vkr.blockchain.dto.UpdateAccountRolesPayload;
import ru.vkr.blockchain.exception.user.UserNotFoundException;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.repository.PeerRepository;
import ru.vkr.blockchain.repository.entity.AuditLogRepository;
import ru.vkr.blockchain.domain.entity.AuditLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Применяет транзакции к состоянию при создании блока.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionApplierService {

    private static final String ENTITY_ACCOUNT = "ACCOUNT";
    private static final String ENTITY_TRANSACTION = "TRANSACTION";

    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final PeerRepository peerRepository;
    private final AuditLogRepository auditLogRepository;
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
            case STORE_FILE -> applyStoreFile(transaction);
            case ADD_PEER -> applyAddPeer(transaction);
            case REMOVE_PEER -> applyRemovePeer(transaction);
        }

        transaction.setStatus(TransactionStatus.CONFIRMED);
        log.debug("Applied transaction {} type={}", transaction.getId(), transaction.getTransactionType());
    }

    private void applyCreateAccount(Transaction tx) {
        CreateAccountPayload payload = parsePayload(tx.getPayload(), CreateAccountPayload.class);
        String publicKeyBase64 = payload.getPublicKey();
        String address = cryptoService.generateAddress(publicKeyBase64);

        if (accountRepository.findByAddress(address).isPresent()) {
            throw new IllegalStateException("Account already exists: " + address);
        }

        Account account = new Account(address, publicKeyBase64, tx.getSenderId());
        account.setAccountRoles(new ArrayList<>(List.of(AccountRole.USER)));
        try {
            accountRepository.save(account);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save account", e);
        }
        auditLogRepository.save(new AuditLog(ENTITY_ACCOUNT, address, "CREATE_ACCOUNT", tx.getSenderId(),
                "Transaction " + tx.getId()));
    }

    private void applyUpdateAccountRoles(Transaction tx) {
        UpdateAccountRolesPayload payload = parsePayload(tx.getPayload(), UpdateAccountRolesPayload.class);
        String targetAddress = payload.getTargetAddress();
        Account account = accountRepository.findByAddress(targetAddress)
                .orElseThrow(() -> new UserNotFoundException(targetAddress));

        if (!account.isActive()) {
            throw new IllegalStateException("Cannot update roles for inactive account: " + targetAddress);
        }
        if (accountRepository.isBootstrapValidator(targetAddress)) {
            throw new IllegalStateException("Cannot change roles of bootstrap validator: " + targetAddress);
        }

        boolean hadValidatorRole = account.getAccountRoles().contains(AccountRole.VALIDATOR);
        boolean willHaveValidatorRole = payload.getRoles().contains(AccountRole.VALIDATOR);

        account.setAccountRoles(new ArrayList<>(payload.getRoles()));
        try {
            accountRepository.update(account);
            syncValidatorsList(targetAddress, hadValidatorRole, willHaveValidatorRole);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update account", e);
        }
        auditLogRepository.save(new AuditLog(ENTITY_ACCOUNT, targetAddress, "UPDATE_ROLES", tx.getSenderId(),
                "Transaction " + tx.getId() + ", roles: " + payload.getRoles()));
    }

    private void syncValidatorsList(String address, boolean hadValidatorRole, boolean willHaveValidatorRole)
            throws IOException {
        if (willHaveValidatorRole && !hadValidatorRole) {
            accountRepository.addValidator(address);
        } else if (!willHaveValidatorRole && hadValidatorRole) {
            accountRepository.removeValidator(address);
        }
    }

    private void applyDeactivateAccount(Transaction tx) {
        DeactivateAccountPayload payload = parsePayload(tx.getPayload(), DeactivateAccountPayload.class);
        Account account = accountRepository.findByAddress(payload.getTargetAddress())
                .orElseThrow(() -> new UserNotFoundException(payload.getTargetAddress()));

        if (!account.isActive()) {
            throw new IllegalStateException("Account already inactive: " + payload.getTargetAddress());
        }
        if (accountRepository.isBootstrapValidator(payload.getTargetAddress())) {
            throw new IllegalStateException("Cannot deactivate bootstrap validator: " + payload.getTargetAddress());
        }

        account.setActive(false);
        try {
            accountRepository.update(account);
            if (account.getAccountRoles().contains(AccountRole.VALIDATOR)) {
                accountRepository.removeValidator(payload.getTargetAddress());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to deactivate account", e);
        }
        auditLogRepository.save(new AuditLog(ENTITY_ACCOUNT, payload.getTargetAddress(), "DEACTIVATE_ACCOUNT", tx.getSenderId(),
                "Transaction " + tx.getId()));
    }

    private void applyStoreData(Transaction tx) {
        auditLogRepository.save(new AuditLog(ENTITY_TRANSACTION, tx.getId(), "STORE_DATA", tx.getSenderId(),
                "Transaction " + tx.getId()));
    }

    private void applyStoreFile(Transaction tx) {
        auditLogRepository.save(new AuditLog(ENTITY_TRANSACTION, tx.getId(), "STORE_FILE", tx.getSenderId(),
                "Transaction " + tx.getId()));
    }

    private void applyAddPeer(Transaction tx) {
        AddPeerPayload payload = parsePayload(tx.getPayload(), AddPeerPayload.class);
        if (payload.getPeerUrl() == null || payload.getPeerUrl().isBlank()) {
            throw new IllegalArgumentException("peer_url must not be empty");
        }
        peerRepository.add(payload.getPeerUrl());
        auditLogRepository.save(new AuditLog("PEER", payload.getPeerUrl(), "ADD_PEER", tx.getSenderId(),
                "Transaction " + tx.getId()));
    }

    private void applyRemovePeer(Transaction tx) {
        RemovePeerPayload payload = parsePayload(tx.getPayload(), RemovePeerPayload.class);
        if (payload.getPeerUrl() == null || payload.getPeerUrl().isBlank()) {
            throw new IllegalArgumentException("peer_url must not be empty");
        }
        peerRepository.remove(payload.getPeerUrl());
        auditLogRepository.save(new AuditLog("PEER", payload.getPeerUrl(), "REMOVE_PEER", tx.getSenderId(),
                "Transaction " + tx.getId()));
    }

    private <T> T parsePayload(String payloadJson, Class<T> clazz) {
        try {
            return objectMapper.readValue(payloadJson, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload format: " + e.getMessage());
        }
    }
}
