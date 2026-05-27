package ru.vkr.blockchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.AddPeerPayload;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.dto.RemovePeerPayload;
import ru.vkr.blockchain.repository.PeerRepository;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.service.domain.TransactionService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PeerService {

    private final PeerRepository peerRepository;
    private final TransactionService transactionService;
    private final PendingTransactionRepository pendingTransactionRepository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public List<String> listPeers() {
        return peerRepository.findAll();
    }

    @RequireRole(AccountRole.VALIDATOR)
    public void addPeer(CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.ADD_PEER) {
            throw new IllegalArgumentException("transaction_type must be ADD_PEER");
        }
        if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
            throw new SecurityException("Invalid signature for ADD_PEER transaction");
        }
        AddPeerPayload payload = parsePayload(request.getPayload(), AddPeerPayload.class);
        if (payload.getPeerUrl() == null || payload.getPeerUrl().isBlank()) {
            throw new IllegalArgumentException("peer_url must not be empty");
        }
        Transaction tx = transactionService.createTransaction(request);
        pendingTransactionRepository.save(tx);
    }

    @RequireRole(AccountRole.VALIDATOR)
    public void removePeer(CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.REMOVE_PEER) {
            throw new IllegalArgumentException("transaction_type must be REMOVE_PEER");
        }
        if (!cryptoService.checkSignatureValid(request.getPayload(), request.getSignature(), request.getCreatorPublicKey())) {
            throw new SecurityException("Invalid signature for REMOVE_PEER transaction");
        }
        RemovePeerPayload payload = parsePayload(request.getPayload(), RemovePeerPayload.class);
        if (payload.getPeerUrl() == null || payload.getPeerUrl().isBlank()) {
            throw new IllegalArgumentException("peer_url must not be empty");
        }
        Transaction tx = transactionService.createTransaction(request);
        pendingTransactionRepository.save(tx);
    }

    private <T> T parsePayload(String payloadJson, Class<T> clazz) {
        try {
            return objectMapper.readValue(payloadJson, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload format: " + e.getMessage());
        }
    }
}
