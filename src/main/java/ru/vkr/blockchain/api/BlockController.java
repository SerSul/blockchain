package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.CreateBlockRequest;
import ru.vkr.blockchain.service.BlockCreationService;
import ru.vkr.blockchain.service.ValidatorSelectionService;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
@Slf4j
public class BlockController {

    private final ValidatorSelectionService validatorSelectionService;
    private final BlockCreationService blockCreationService;

    @GetMapping("/next-validator")
    public ResponseEntity<ApiResponse<Account>> getNextValidator() {
        return validatorSelectionService.getNextValidator()
                .map(validator -> ResponseEntity.ok(ApiResponse.success(validator)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @PostMapping
    public ResponseEntity<?> createBlock(@RequestBody @Valid CreateBlockRequest request) {
        blockCreationService.createBlock(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
