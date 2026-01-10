package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.service.BlockChainService;
import ru.vkr.blockchain.service.domain.AccountService;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    private final BlockChainService blockChainService;

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody @Valid CreateTransactionRequest request) {
        blockChainService.createAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success());

    }
}
