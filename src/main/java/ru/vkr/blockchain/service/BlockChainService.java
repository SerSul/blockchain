package ru.vkr.blockchain.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.service.domain.TransactionService;

import java.util.Collection;

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

    private final CryptoService cryptoService;
    private final TransactionService transactionService;
    private final StoreDataPayloadValidator storeDataPayloadValidator;

    private final PendingTransactionRepository pendingTransactionRepository;

    @RequireRole({AccountRole.USER, AccountRole.VALIDATOR})
    public void storeData(CreateTransactionRequest request) {
        if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
            throw new SecurityException("Invalid signature for transaction");
        }

        Transaction transaction = transactionService.createTransaction(request);
        pendingTransactionRepository.save(transaction);
    }

    @RequireRole({AccountRole.USER, AccountRole.VALIDATOR})
    public void storeFile(CreateTransactionRequest request) {
        if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
            throw new SecurityException("Invalid signature for transaction");
        }

        storeDataPayloadValidator.validateFilePayload(request.getPayload());

        Transaction transaction = transactionService.createTransaction(request);
        pendingTransactionRepository.save(transaction);
    }

    public Collection<Transaction> getPendingTransactions() {
        return pendingTransactionRepository.findAll();
    }

}
