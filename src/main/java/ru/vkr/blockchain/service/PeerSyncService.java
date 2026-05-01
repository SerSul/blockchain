package ru.vkr.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.vkr.blockchain.config.BlockchainBootstrapProperties;
import ru.vkr.blockchain.domain.entity.BlockMetadata;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.dto.BlockDto;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.mapper.BlockMapper;
import ru.vkr.blockchain.mapper.TransactionMapper;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.repository.PeerRepository;
import ru.vkr.blockchain.repository.entity.TransactionMetadataRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeerSyncService {

    private final PeerRepository peerRepository;
    private final PendingTransactionRepository pendingTransactionRepository;
    private final TransactionMetadataRepository transactionMetadataRepository;
    private final BlockQueryService blockQueryService;
    private final BlockCreationService blockCreationService;
    private final BlockchainBootstrapProperties bootstrapProperties;
    private final BlockMapper blockMapper;
    private final TransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Scheduled(fixedDelayString = "${blockchain.sync.interval-ms:15000}")
    public void syncFromPeers() {
        if (!bootstrapProperties.isSyncEnabled()) {
            return;
        }
        List<String> peers = peerRepository.findAll();
        if (peers.isEmpty()) {
            return;
        }
        for (String peer : peers) {
            try {
                syncPendingFromPeer(peer);
            } catch (Exception e) {
                log.warn("Pending sync failed for {}: {}", peer, e.getMessage());
            }
        }
        int localHeight = blockQueryService.getLatestMetadata().map(BlockMetadata::getHeight).orElse(-1);
        List<String> peersByHeight = peers.stream()
                .map(peer -> new java.util.AbstractMap.SimpleEntry<>(peer, getRemoteTipHeight(peer)))
                .filter(entry -> entry.getValue() > localHeight)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(java.util.Map.Entry::getKey)
                .toList();
        if (peersByHeight.isEmpty()) {
            return;
        }
        for (String peer : peersByHeight) {
            try {
                syncOnePeer(peer);
            } catch (Exception e) {
                log.warn("Peer sync failed for {}: {}", peer, e.getMessage());
            }
        }
    }

    private void syncPendingFromPeer(String peerBaseUrl) throws Exception {
        int limit = Math.max(1, bootstrapProperties.getSyncBatchSize());
        String body = restClient.get()
                .uri(peerBaseUrl + "/api/transactions/pending?page=0&size={limit}", limit)
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
            return;
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode content = root.path("data").path("content");
        if (!content.isArray() || content.isEmpty()) {
            return;
        }
        List<TransactionDto> pending = objectMapper.readerForListOf(TransactionDto.class).readValue(content);
        int imported = 0;
        for (TransactionDto dto : pending) {
            if (dto.getId() == null || dto.getId().isBlank()) {
                continue;
            }
            if (pendingTransactionRepository.existsById(dto.getId()) || transactionMetadataRepository.existsById(dto.getId())) {
                continue;
            }
            Transaction tx = transactionMapper.toDomain(dto);
            if (tx.getStatus() == null) {
                tx.setStatus(TransactionStatus.PENDING);
            }
            if (!tx.isValid()) {
                continue;
            }
            pendingTransactionRepository.save(tx);
            imported++;
        }
        if (imported > 0) {
            log.info("Pending sync imported {} tx from {}", imported, peerBaseUrl);
        }
    }

    private void syncOnePeer(String peerBaseUrl) throws Exception {
        int localHeight = blockQueryService.getLatestMetadata()
                .map(BlockMetadata::getHeight)
                .orElse(-1);
        int fromHeight = localHeight + 1;
        int limit = Math.max(1, bootstrapProperties.getSyncBatchSize());

        String body = restClient.get()
                .uri(peerBaseUrl + "/api/blocks/range?fromHeight={fromHeight}&limit={limit}", fromHeight, limit)
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
            return;
        }

        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return;
        }

        List<BlockDto> blocks = objectMapper.readerForListOf(BlockDto.class).readValue(data);
        int imported = 0;
        for (BlockDto dto : blocks) {
            BlockCreationService.ImportResult result = blockCreationService.importExternalBlock(blockMapper.toDomain(dto), peerBaseUrl);
            if (result == BlockCreationService.ImportResult.IMPORTED) {
                imported++;
            }
        }
        if (imported > 0) {
            Optional<BlockMetadata> latest = blockQueryService.getLatestMetadata();
            log.info("Peer sync imported {} blocks from {}, localHeight={}", imported, peerBaseUrl, latest.map(BlockMetadata::getHeight).orElse(-1));
        }
    }

    private int getRemoteTipHeight(String peerBaseUrl) {
        try {
            String body = restClient.get()
                    .uri(peerBaseUrl + "/api/blocks?page=0&size=1&sortBy=height&sortDir=desc")
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return -1;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("data").path("content");
            if (!content.isArray() || content.isEmpty()) {
                return -1;
            }
            return content.get(0).path("height").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }
}
