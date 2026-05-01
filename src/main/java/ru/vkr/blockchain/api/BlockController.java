package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import ru.vkr.blockchain.domain.entity.BlockMetadata;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.enums.BlockStatus;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.BlockDto;
import ru.vkr.blockchain.dto.CreateBlockRequest;
import ru.vkr.blockchain.dto.PageResponse;
import ru.vkr.blockchain.dto.PrepareBlockRequest;
import ru.vkr.blockchain.dto.PrepareBlockResponse;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.mapper.BlockMapper;
import ru.vkr.blockchain.service.BlockCreationService;
import ru.vkr.blockchain.service.BlockQueryService;
import ru.vkr.blockchain.service.ValidatorSelectionService;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
@Slf4j
public class BlockController {

    private final ValidatorSelectionService validatorSelectionService;
    private final BlockCreationService blockCreationService;
    private final BlockQueryService blockQueryService;
    private final BlockMapper blockMapper;

    @GetMapping("/next-validator")
    public ResponseEntity<ApiResponse<Account>> getNextValidator() {
        return validatorSelectionService.getNextValidator()
                .map(validator -> ResponseEntity.ok(ApiResponse.success(validator)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    /**
     * Подготовка блока: возвращает hash_to_sign (подписать им) и timestamp, transaction_ids
     * для запроса createBlock. Вызвать prepare → подписать hash_to_sign → createBlock с теми же данными.
     */
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<PrepareBlockResponse>> prepareBlock(@RequestBody(required = false) PrepareBlockRequest request) {
        List<String> transactionIds = request != null && request.getTransactionIds() != null ? request.getTransactionIds() : List.of();
        PrepareBlockResponse response = blockCreationService.prepareBlock(transactionIds);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<?> createBlock(@RequestBody @Valid CreateBlockRequest request) {
        blockCreationService.createBlock(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Список блоков с фильтрами и пагинацией.
     * Параметры: heightFrom, heightTo, validatorAddress, page (0), size (20), sortBy (height), sortDir (desc).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BlockMetadata>>> getBlocks(
            @RequestParam(required = false) Integer heightFrom,
            @RequestParam(required = false) Integer heightTo,
            @RequestParam(required = false) String validatorAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "height") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PageResponse<BlockMetadata> result = blockQueryService.getBlocks(heightFrom, heightTo, validatorAddress, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Блок по хешу с полным списком транзакций.
     */
    @GetMapping("/{hash}")
    public ResponseEntity<ApiResponse<BlockDto>> getBlockByHash(@PathVariable String hash) {
        return blockQueryService.getBlockByHash(hash)
                .map(dto -> ResponseEntity.ok(ApiResponse.success(dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Транзакции блока по хешу блока.
     */
    @GetMapping("/{hash}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getBlockTransactions(@PathVariable String hash) {
        List<TransactionDto> transactions = blockQueryService.getBlockTransactions(hash);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @GetMapping("/range")
    public ResponseEntity<ApiResponse<List<BlockDto>>> getBlocksRange(
            @RequestParam int fromHeight,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(blockQueryService.getBlocksRange(fromHeight, limit)));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<String>> importBlockFromPeer(@RequestBody @Valid BlockDto blockDto) {
        Block block = blockMapper.toDomain(blockDto);
        if (block.getStatus() == null) {
            block.setStatus(BlockStatus.CONFIRMED);
        }
        BlockCreationService.ImportResult result = blockCreationService.importExternalBlock(block, "peer-push");
        return ResponseEntity.ok(ApiResponse.success(result.name()));
    }
}
