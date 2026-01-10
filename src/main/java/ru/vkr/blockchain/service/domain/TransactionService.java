package ru.vkr.blockchain.service.domain;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.api.CreateTransactionRequest;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.service.CryptoService;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final CryptoService cryptoService;

    public Transaction createTransaction(CreateTransactionRequest createTransactionRequest) {
        var requesterAddress = cryptoService.generateAddress(createTransactionRequest.getCreatorPublicKey());
        return new Transaction(requesterAddress, createTransactionRequest.getPayload(), createTransactionRequest.getContentType(),
                createTransactionRequest.getTransactionType(), createTransactionRequest.getSignature());

    }
}
