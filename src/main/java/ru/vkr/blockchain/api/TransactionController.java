package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.dto.PageResponse;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.service.BlockChainService;
import ru.vkr.blockchain.service.FileStorageService;
import ru.vkr.blockchain.service.TransactionQueryService;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final BlockChainService blockChainService;
    private final FileStorageService fileStorageService;
    private final TransactionQueryService transactionQueryService;

    @PostMapping("/store")
    public ResponseEntity<?> storeData(@RequestBody @Valid CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.STORE_DATA) {
            log.warn("API storeData rejected: invalid txType={}", request.getTransactionType());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("transaction_type must be STORE_DATA"));
        }
        log.info("API storeData accepted");
        blockChainService.storeData(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Загрузка файла в MinIO и регистрация STORE_FILE транзакции.
     * payload — JSON: {"fileName":"...", "fileHash":"<sha256>", "size":123, "previous_transaction_id":"<optional>"}
     */
    @PostMapping(value = "/store-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> storeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("creator_public_key") String creatorPublicKey,
            @RequestParam("payload") String payload,
            @RequestParam("signature") String signature,
            @RequestParam(value = "content_type", required = false) String contentType) {

        CreateTransactionRequest request = new CreateTransactionRequest(
                creatorPublicKey,
                TransactionType.STORE_FILE,
                payload,
                contentType,
                signature
        );

        log.info("API storeFile fileName={}, size={}", file.getOriginalFilename(), file.getSize());
        String transactionId = fileStorageService.storeFile(request, file);
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of(
                "transaction_id", transactionId,
                "message", "File uploaded and STORE_FILE transaction added to pending pool"
        )));
    }

    @GetMapping("/files/{fileHash}")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String fileHash) {
        log.info("API downloadFile fileHash={}", fileHash);
        var stored = fileStorageService.download(fileHash);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (stored.contentType() != null && !stored.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(stored.contentType());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileHash + "\"")
                .contentType(mediaType)
                .contentLength(stored.size())
                .body(new InputStreamResource(stored.stream()));
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
        log.info("API getTransactions status={}, blockHash={}, senderId={}, txType={}, page={}, size={}",
                status, blockHash, senderId, transactionType, page, size);
        TransactionStatus statusEnum = parseStatus(status);
        TransactionType typeEnum = parseTransactionType(transactionType);
        PageResponse<TransactionDto> result = transactionQueryService.getTransactions(
                statusEnum, blockHash, senderId, typeEnum, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<TransactionDto>>> getPendingTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        log.info("API getPendingTransactions page={}, size={}", page, size);
        return ResponseEntity.ok(ApiResponse.success(transactionQueryService.getPendingTransactionsPage(page, size)));
    }

    /**
     * Одна транзакция по ID (из подтверждённых в блоках или из pending).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransactionById(@PathVariable String id) {
        log.info("API getTransactionById id={}", id);
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
