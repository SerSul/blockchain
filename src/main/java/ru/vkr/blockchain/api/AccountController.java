package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.service.AccountService;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody @Valid CreateTransactionRequest request) {
        log.info("API createAccount requested, txType={}", request.getTransactionType());
        accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success());
    }

    @PatchMapping("/roles")
    public ResponseEntity<?> updateAccountRoles(@RequestBody @Valid CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.UPDATE_ACCOUNT_ROLES) {
            log.warn("API updateAccountRoles rejected: invalid txType={}", request.getTransactionType());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("transaction_type must be UPDATE_ACCOUNT_ROLES"));
        }
        log.info("API updateAccountRoles accepted");
        accountService.updateAccountRoles(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/deactivate")
    public ResponseEntity<?> deactivateAccount(@RequestBody @Valid CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.DEACTIVATE_ACCOUNT) {
            log.warn("API deactivateAccount rejected: invalid txType={}", request.getTransactionType());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("transaction_type must be DEACTIVATE_ACCOUNT"));
        }
        log.info("API deactivateAccount accepted");
        accountService.deactivateAccount(request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
