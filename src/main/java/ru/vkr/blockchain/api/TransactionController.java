package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.service.BlockChainService;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final BlockChainService blockChainService;

    @PostMapping("/store")
    public ResponseEntity<?> storeData(@RequestBody @Valid CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.STORE_DATA) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("transaction_type must be STORE_DATA"));
        }
        blockChainService.storeData(request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
