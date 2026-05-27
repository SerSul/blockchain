package ru.vkr.blockchain.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.NetworkJoinSnapshotDto;
import ru.vkr.blockchain.dto.NetworkStatusDto;
import ru.vkr.blockchain.dto.NetworkValidatorDto;
import ru.vkr.blockchain.service.NetworkOverviewService;

import java.util.List;

@RestController
@RequestMapping("/api/network")
@RequiredArgsConstructor
@Slf4j
public class NetworkController {

    private final NetworkOverviewService networkOverviewService;

    @GetMapping("/validators")
    public ResponseEntity<ApiResponse<List<NetworkValidatorDto>>> getValidators() {
        log.info("API getNetworkValidators");
        return ResponseEntity.ok(ApiResponse.success(networkOverviewService.getValidators()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<NetworkStatusDto>> getStatus() {
        log.info("API getNetworkStatus");
        return ResponseEntity.ok(ApiResponse.success(networkOverviewService.getStatus()));
    }

    /**
     * Снимок для присоединения ноды: аккаунты валидаторов и round-robin (не в блокчейне, только LevelDB).
     */
    @GetMapping("/join-snapshot")
    public ResponseEntity<ApiResponse<NetworkJoinSnapshotDto>> getJoinSnapshot() {
        log.info("API getNetworkJoinSnapshot");
        return ResponseEntity.ok(ApiResponse.success(networkOverviewService.buildJoinSnapshot()));
    }
}
