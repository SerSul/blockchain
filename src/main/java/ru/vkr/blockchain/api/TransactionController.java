package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.dto.PageResponse;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.service.BlockChainService;
import ru.vkr.blockchain.service.TransactionQueryService;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final BlockChainService blockChainService;
    private final TransactionQueryService transactionQueryService;

    @PostMapping("/store")
    public ResponseEntity<?> storeData(@RequestBody @Valid CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.STORE_DATA) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("transaction_type must be STORE_DATA"));
        }
        blockChainService.storeData(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Список транзакций с фильтрами и пагинацией.
     * Параметры: status (PENDING, CONFIRMED, ...), blockHash, senderId, transactionType,
     * page (0), size (20), sortBy (timestamp), sortDir (desc).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TransactionDto>>> getTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String blockHash,
            @RequestParam(required = false) String senderId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        TransactionStatus statusEnum = parseStatus(status);
        TransactionType typeEnum = parseTransactionType(transactionType);
        PageResponse<TransactionDto> result = transactionQueryService.getTransactions(
                statusEnum, blockHash, senderId, typeEnum, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Одна транзакция по ID (из подтверждённых в блоках или из pending).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransactionById(@PathVariable String id) {
        return transactionQueryService.getTransactionById(id)
                .map(dto -> ResponseEntity.ok(ApiResponse.success(dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    private static TransactionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TransactionStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static TransactionType parseTransactionType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TransactionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
