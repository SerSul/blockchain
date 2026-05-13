package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.service.PeerService;

import java.util.List;

@RestController
@RequestMapping("/api/peers")
@RequiredArgsConstructor
@Slf4j
public class PeerController {

    private final PeerService peerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> listPeers() {
        log.info("API listPeers");
        return ResponseEntity.ok(ApiResponse.success(peerService.listPeers()));
    }

    @PostMapping
    public ResponseEntity<?> addPeer(@RequestBody @Valid CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.ADD_PEER) {
            log.warn("API addPeer rejected: invalid txType={}", request.getTransactionType());
            return ResponseEntity.badRequest().body(ApiResponse.error("transaction_type must be ADD_PEER"));
        }
        log.info("API addPeer accepted");
        peerService.addPeer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success());
    }

    @PostMapping("/remove")
    public ResponseEntity<?> removePeer(@RequestBody @Valid CreateTransactionRequest request) {
        if (request.getTransactionType() != TransactionType.REMOVE_PEER) {
            log.warn("API removePeer rejected: invalid txType={}", request.getTransactionType());
            return ResponseEntity.badRequest().body(ApiResponse.error("transaction_type must be REMOVE_PEER"));
        }
        log.info("API removePeer accepted");
        peerService.removePeer(request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
